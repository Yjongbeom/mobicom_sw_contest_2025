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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
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

        RestTemplate restTemplate = new RestTemplate();
        String framePageUrl = "https://www.law.go.kr" + detailPath;

        log.info("1단계 접속 시도: {}", framePageUrl);
        ResponseEntity<String> frameResponse = restTemplate.getForEntity(framePageUrl, String.class);
        if (frameResponse.getStatusCode() != HttpStatus.OK) {
            log.error("껍데기 페이지 접속 실패: {}", frameResponse.getStatusCode());
            return "";
        }

        Document frameDoc = Jsoup.parse(frameResponse.getBody());
        Element iframe = frameDoc.selectFirst("iframe#lawService");
        if (iframe == null) {
            log.error("껍데기 페이지에서 iframe을 찾지 못했습니다.");
            return "";
        }

        String iframeSrc = iframe.attr("src");
        if (iframeSrc.isBlank()) {
            log.error("iframe에 src 속성이 없습니다.");
            return "";
        }

        String realContentUrl;
        if (iframeSrc.startsWith("http")) {
            realContentUrl = iframeSrc;
        } else {
            realContentUrl = "https://www.law.go.kr" + iframeSrc;
        }

        // ================== [KEY FIX] ==================
        // 2단계 접속 시, 실제 브라우저처럼 보이도록 HTTP 헤더를 추가합니다.
        HttpHeaders headers = new HttpHeaders();
        headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/108.0.0.0 Safari/537.36");
        headers.set("Referer", framePageUrl); // '이전 페이지(액자 페이지)'에서 접속한 것처럼 보이게 함

        HttpEntity<String> entity = new HttpEntity<>(headers);

        log.info("2단계 접속 시도 (진짜 본문): {}", realContentUrl);
        // restTemplate.getForEntity 대신 exchange를 사용하여 헤더를 포함한 요청을 보냅니다.
        ResponseEntity<String> contentResponse = restTemplate.exchange(realContentUrl, HttpMethod.GET, entity, String.class);
        // ===============================================

        if (contentResponse.getStatusCode() == HttpStatus.OK) {
            return parseLawContent(contentResponse.getBody());
        }

        throw new Exception("법령 본문 페이지 조회 실패: " + contentResponse.getStatusCode());
    }

    private String parseLawContent(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        Document doc = Jsoup.parse(html);
        Elements contentElements = doc.select("#contentBody");
        if (contentElements.isEmpty()) {
            contentElements = doc.select(".lawcon");
        }
        return contentElements.text();
    }

    // Other methods remain the same
    public List<LawInfo> getLawsByContractId(Long contractId) {
        return lawInfoRepository.findByContractContractId(contractId);
    }

    public LawInfo getLawById(Long lawInfoId) {
        return lawInfoRepository.findById(lawInfoId)
                .orElseThrow(() -> new IllegalArgumentException("법률 정보를 찾을 수 없습니다."));
    }
}
