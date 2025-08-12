package contest.mobicom_contest.law.service;

import contest.mobicom_contest.contract.client.LawApiClient;
import contest.mobicom_contest.contract.client.OpenAiClient;
import contest.mobicom_contest.contract.dto.Issue;
import contest.mobicom_contest.contract.model.Contract;
import contest.mobicom_contest.contract.model.ContractRepository;
import contest.mobicom_contest.law.dto.LawAnalyzeDto;
import contest.mobicom_contest.law.model.LawInfo;
import contest.mobicom_contest.law.model.LawInfoRepository;
import contest.mobicom_contest.member.model.Member;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
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
import contest.mobicom_contest.law.dto.LawInfoDTO;

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

        log.debug("분석할 OCR 텍스트:\n{}", contract.getOcrText());


        Member member = contract.getMember();
        String targetLanguage = member.getLanguage();

        log.debug("타겟 언어:\n{}", targetLanguage);

        List<Issue> issues = openAiClient.detectUnfairClauses(contract.getOcrText());
        Map<Issue, List<LawInfo>> issueLawMap = new HashMap<>();

        log.debug("발견된 이슈 개수: {}", issues.size());

        for (Issue issue : issues) {
            List<LawInfo> laws = lawApiClient.searchRelatedLaws(issue.getType(), contract);
            log.debug("{} 이슈에 대한 법률 개수: {}", issue.getType(), laws.size());
            laws.forEach(law -> {
                try {
                    String lawContent = fetchLawDetailContent(law.getDetailUrl());

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
                    log.error("법령 상세 조회 실패: {}", e.getMessage());
                }
            });

            try {
                lawInfoRepository.saveAll(laws);
            } catch (DataIntegrityViolationException e) {
                log.error("DB 저장 실패: {}", e.getRootCause().getMessage());
                throw new RuntimeException("법령 정보 저장 실패", e);
            }

            issueLawMap.put(issue, laws);
        }

        return new LawAnalyzeDto(
                contract.getContractId(),
                issues,
                issueLawMap.values().stream()
                        .flatMap(List::stream)
                        .map(LawInfoDTO::new) //
                        .collect(Collectors.toList())
        );
    }

    private String fetchLawDetailContent(String detailPath) throws Exception {
        if (detailPath == null || detailPath.isBlank()) {
            throw new IllegalArgumentException("법령 상세 경로가 유효하지 않습니다.");
        }

        String detailUrl = "https://www.law.go.kr" + detailPath;
        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<String> response = restTemplate.getForEntity(detailUrl, String.class);

        if (response.getStatusCode() == HttpStatus.OK) {
            String html = response.getBody();

            // HTML 구조 로깅 (디버깅용)
            log.debug("법령 상세 HTML (앞 500자): {}",
                    html != null && html.length() > 500 ?
                            html.substring(0, 500) : html);

            return parseLawContent(response.getBody());
        }
        throw new Exception("법령 상세 조회 실패: " + response.getStatusCode());
    }

    private String parseLawContent(String html) {
        Document doc = Jsoup.parse(html);
        Elements contentElements = doc.select(".lawcon");
        return contentElements.eachText().stream().collect(Collectors.joining("\n"));
    }


    public List<LawInfo> getLawsByContractId(Long contractId) {
        return lawInfoRepository.findByContractContractId(contractId);
    }

    public LawInfo getLawById(Long lawInfoId) {
        return lawInfoRepository.findById(lawInfoId)
                .orElseThrow(() -> new IllegalArgumentException("법률 정보를 찾을 수 없습니다."));
    }
}

