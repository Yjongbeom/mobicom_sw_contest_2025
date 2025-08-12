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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

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

        // 1. AI를 통해 계약서에서 한국어로 된 법적 쟁점(issue) 감지
        List<Issue> issues = openAiClient.detectUnfairClauses(contract.getOcrText());
        Map<Issue, List<LawInfo>> issueLawMap = new HashMap<>();

        for (Issue issue : issues) {
            // 2. 번역하기 전, 원본 'type'(한글)으로 먼저 법률을 검색
            List<LawInfo> laws = lawApiClient.searchRelatedLaws(issue.getType(), contract);

            // 3. 검색된 각 법률의 내용을 처리
            laws.forEach(law -> {
                try {
                    String lawContent = lawApiClient.fetchLawDetailByApi(law.getLawSerialNumber());
                    if (!StringUtils.hasText(lawContent)) {
                        log.warn("법률 '{}'의 상세 내용을 API로 가져오지 못했습니다. 분석을 건너뜁니다.", law.getLawName());
                        return;
                    }
                    law.setTranslatedLawName(openAiClient.translateText(law.getLawName(), targetLanguage));
                    String summary = openAiClient.summarizeAndTranslate(lawContent, targetLanguage);
                    law.setTranslatedSummary(summary);
                    law.setContract(contract);
                } catch (Exception e) {
                    log.error("법령 상세 조회 또는 AI 처리 실패: law={}, error={}", law.getLawName(), e.getMessage());
                }
            });

            try {
                List<LawInfo> validLaws = laws.stream()
                        .filter(l -> l.getTranslatedSummary() != null && !l.getTranslatedSummary().isBlank())
                        .collect(Collectors.toList());
                lawInfoRepository.saveAll(validLaws);
                issueLawMap.put(issue, validLaws);
            } catch (DataIntegrityViolationException e) {
                log.error("DB 저장 실패: {}", e.getRootCause() != null ? e.getRootCause().getMessage() : e.getMessage());
                throw new RuntimeException("법령 정보 저장 실패", e);
            }
        }

        // 4. 모든 법률 처리가 끝난 후, 최종적으로 issues 내부 필드들을 번역
        if (!"Korean".equalsIgnoreCase(targetLanguage) && !"ko".equalsIgnoreCase(targetLanguage)) {
             for (Issue issue : issues) {
                 issue.setType(openAiClient.translateText(issue.getType(), targetLanguage));
                 issue.setReason(openAiClient.translateText(issue.getReason(), targetLanguage));
                 issue.setEvidence(openAiClient.translateText(issue.getEvidence(), targetLanguage));
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
    
    public List<LawInfo> getLawsByContractId(Long contractId) {
        return lawInfoRepository.findByContractContractId(contractId);
    }

    public LawInfo getLawById(Long lawInfoId) {
        return lawInfoRepository.findById(lawInfoId)
                .orElseThrow(() -> new IllegalArgumentException("법률 정보를 찾을 수 없습니다."));
    }
}
