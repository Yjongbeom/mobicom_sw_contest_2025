package contest.mobicom_contest.law.service;

import contest.mobicom_contest.contract.client.LawApiClient;
import contest.mobicom_contest.contract.client.OpenAiClient;
import contest.mobicom_contest.contract.dto.Issue;
import contest.mobicom_contest.contract.model.Contract;
import contest.mobicom_contest.contract.model.ContractRepository;
import contest.mobicom_contest.law.dto.LawAnalyzeDto;
import contest.mobicom_contest.law.dto.LawInfoDTO;
import contest.mobicom_contest.law.model.LawInfo;
import contest.mobicom_contest.law.model.LawInfoRepository;
import contest.mobicom_contest.member.model.Member;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LawService {

    private final LawInfoRepository lawInfoRepository;
    private final OpenAiClient openAiClient;
    private final LawApiClient lawApiClient;
    private final ContractRepository contractRepository;

    public LawAnalyzeDto analyzeLegalIssues(Long contractId) throws Exception {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new IllegalArgumentException("계약서 없음"));

        Member member = contract.getMember();
        // 람다에서 사용하기 위해 final 변수로 선언
        final String targetLanguage = (member.getLanguage() == null || member.getLanguage().isBlank())
                ? "English"
                : member.getLanguage();

        List<Issue> issues = openAiClient.detectUnfairClauses(contract.getOcrText());
        Map<Issue, List<LawInfo>> issueLawMap = new HashMap<>();

        for (Issue issue : issues) {
            List<LawInfo> laws = lawApiClient.searchRelatedLaws(issue.getType(), contract);

            laws.forEach(law -> {
                try {
                    String lawContent = fetchLawDetailContent(law.getDetailUrl());

                    if (lawContent == null || lawContent.isBlank()) {
                        log.warn("법률 '{}'의 내용을 가져오지 못해 처리를 건너뜁니다.", law.getLawName());
                        return;
                    }

                    law.setTranslatedLawName(
                            openAiClient.translateText(law.getLawName(), targetLanguage)
                    );

                    String summary = openAiClient.summarizeAndTranslate(lawContent, targetLanguage);
                    int maxLen = 3000;
                    if (summary.length() > maxLen) {
                        summary = summary.substring(0, maxLen) + "... [truncated]";
                        log.warn("요약 내용이 {}자를 초과하여 잘렸습니다.", maxLen);
                    }
                    law.setTranslatedSummary(summary);
                    law.setContract(contract);
                } catch (Exception e) {
                    log.error("법령 상세 조회 또는 AI 처리 실패: law={}, error={}", law.getLawName(), e.getMessage());
                }
            });

            try {
                List<LawInfo> validLaws = laws.stream()
                        .filter(law -> law.getTranslatedSummary() != null && !law.getTranslatedSummary().isBlank())
                        .collect(Collectors.toList());

                lawInfoRepository.saveAll(validLaws);
                issueLawMap.put(issue, validLaws);
            } catch (DataIntegrityViolationException e) {
                log.error("DB 저장 실패: {}", e.getRootCause() != null ? e.getRootCause().getMessage() : e.getMessage());
                throw new RuntimeException("법령 정보 저장 실패", e);
            }
        }

        return new LawAnalyzeDto(
                contract.getContractId(),
                issues,
                issueLawMap.values().stream()
                        .flatMap(List::stream)
                        .map(LawInfoDTO::new)
                        .collect(Collectors.toList())
        );
    }

    private String fetchLawDetailContent(String detailPath) throws Exception {
        if (detailPath == null || detailPath.isBlank()) {
            return "";
        }

        // === [핵심 수정] 2단계 스크래핑 로직 ===
        RestTemplate restTemplate = new RestTemplate();
        String framePageUrl = "https://www.law.go.kr" + detailPath;

        // 1단계: <iframe>이 있는 껍데기(액자) 페이지에 접속
        log.info("1단계 접속 시도: {}", framePageUrl);
        ResponseEntity<String> frameResponse = restTemplate.getForEntity(framePageUrl, String.class);
        if (frameResponse.getStatusCode() != HttpStatus.OK) {
            log.error("껍데기 페이지 접속 실패: {}", frameResponse.getStatusCode());
            return "";
        }

        // 껍데기 페이지에서 <iframe> 태그의 src 속성값(진짜 주소) 추출
        Document frameDoc = Jsoup.parse(frameResponse.getBody());
        Element iframe = frameDoc.selectFirst("iframe#lawService");
        if (iframe == null) {
            log.error("껍데기 페이지에서 iframe을 찾지 못했습니다.");
            return "";
        }
        String realContentPath = iframe.attr("src");
        if (realContentPath == null || realContentPath.isBlank()) {
            log.error("iframe에 src 속성이 없습니다.");
            return "";
        }

        String realContentUrl = "https://www.law.go.kr" + realContentPath;

        // 2단계: 추출한 진짜 주소로 다시 접속하여 본문(그림) 스크래핑
        log.info("2단계 접속 시도 (진짜 본문): {}", realContentUrl);
        ResponseEntity<String> contentResponse = restTemplate.getForEntity(realContentUrl, String.class);
        if (contentResponse.getStatusCode() == HttpStatus.OK) {
            return parseLawContent(contentResponse.getBody());
        }

        throw new Exception("법령 본문 페이지 조회 실패: " + contentResponse.getStatusCode());
    }

    private String parseLawContent(String html) {
        Document doc = Jsoup.parse(html);
        // 이제 진짜 본문 페이지에 접속했으므로 '#contentBody' 선택자가 동작함
        Elements contentElements = doc.select("#contentBody");
        if(contentElements.isEmpty()){
            // 만약을 대비한 대체 선택자
            contentElements = doc.select(".lawcon");
        }
        return contentElements.text();
    }

    // 나머지 메서드는 기존과 동일
    public List<LawInfo> getLawsByContractId(Long contractId) {
        return lawInfoRepository.findByContractContractId(contractId);
    }

    public LawInfo getLawById(Long lawInfoId) {
        return lawInfoRepository.findById(lawInfoId)
                .orElseThrow(() -> new IllegalArgumentException("법률 정보를 찾을 수 없습니다."));
    }
}
