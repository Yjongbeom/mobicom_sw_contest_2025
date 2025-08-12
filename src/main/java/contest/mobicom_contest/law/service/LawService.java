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
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList; // issues 필드 초기화를 위해 추가
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

    public LawAnalyzeDto analyzeLegalIssues(Long contractId) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new IllegalArgumentException("계약서 없음"));

        Member member = contract.getMember();


        String languageFromMember = member.getLanguage();
        final String targetLanguage = StringUtils.hasText(languageFromMember) ? languageFromMember : "English";
        
        if (!StringUtils.hasText(languageFromMember)) {
            log.warn("Member ID {}의 언어 설정이 비어있어 기본값 'English'를 사용합니다.", member.getId());
        }
        log.info("분석 대상 언어: {}", targetLanguage);

        List<Issue> issues = openAiClient.detectUnfairClauses(contract.getOcrText());
        Map<Issue, List<LawInfo>> issueLawMap = new HashMap<>();

        for (Issue issue : issues) {
            List<LawInfo> laws = lawApiClient.searchRelatedLaws(issue.getType(), contract);

            laws.forEach(law -> {
                try {
                    String lawContent = fetchLawDetailContent(law.getDetailUrl());

                    log.info("법률 [{}]의 상세 내용을 스크래핑했습니다. 내용 길이: {} 자", law.getLawName(), lawContent.length());
                    if (!StringUtils.hasText(lawContent)) {
                        log.warn("법률 [{}]의 상세 내용이 비어있어 AI 분석을 건너뜁니다. URL: {}", law.getLawName(), law.getDetailUrl());
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
                    log.error("법령 상세 정보 처리 중 예외 발생: {}", e.getMessage(), e);
                }
            });

            try {
                List<LawInfo> validLaws = laws.stream()
                                              .filter(l -> StringUtils.hasText(l.getTranslatedSummary()))
                                              .collect(Collectors.toList());
                lawInfoRepository.saveAll(validLaws);
                issueLawMap.put(issue, validLaws);
            } catch (DataIntegrityViolationException e) {
                log.error("DB 저장 실패: {}", e.getRootCause() != null ? e.getRootCause().getMessage() : e.getMessage(), e);
                throw new RuntimeException("법령 정보 저장 실패", e);
            }
        }

        List<LawInfoDTO> allLawInfos = issueLawMap.values().stream()
                .flatMap(List::stream)
                .map(LawInfoDTO::new)
                .collect(Collectors.toList());

        // Lombok @Builder 경고를 해결하기 위해 DTO 생성 방식을 new 키워드로 명시합니다.
        // 또는 LawAnalyzeDto에 @Builder.Default를 추가합니다.
        return new LawAnalyzeDto(
                contract.getContractId(),
                issues,
                allLawInfos
        );
    }

    private String fetchLawDetailContent(String detailPath) {
        if (detailPath == null || detailPath.isBlank()) {
            log.warn("법령 상세 경로가 유효하지 않습니다.");
            return "";
        }

        String detailUrl = "https://www.law.go.kr" + detailPath;
        RestTemplate restTemplate = new RestTemplate();
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(detailUrl, String.class);
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return parseLawContent(response.getBody());
            }
            log.warn("법령 상세 조회 실패: URL={}, Status={}", detailUrl, response.getStatusCode());
        } catch (Exception e) {
            log.error("법령 상세 조회 중 예외 발생: URL={}", detailUrl, e);
        }
        return ""; // 실패 시 빈 문자열 반환
    }

    private String parseLawContent(String html) {
        Document doc = Jsoup.parse(html);
        Elements contentElements = doc.select("#contentBody"); 
        
        if (contentElements.isEmpty()) {
            log.warn("지정한 CSS 셀렉터(#contentBody)로 내용을 찾지 못했습니다. 사이트 구조가 변경되었을 수 있습니다.");
        }
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
