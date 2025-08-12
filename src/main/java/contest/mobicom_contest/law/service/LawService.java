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

    public LawAnalyzeDto analyzeLegalIssues(Long contractId) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new IllegalArgumentException("계약서를 찾을 수 없습니다."));

        Member member = contract.getMember();

        String languageFromMember = member.getLanguage();
        final String targetLanguage = StringUtils.hasText(languageFromMember) ? languageFromMember : "English";
        
        if (!StringUtils.hasText(languageFromMember)) {
            log.warn("Member ID {}의 언어 설정이 비어있어 기본값 'English'를 사용합니다.", member.getId());
        }

        // 1. AI를 통해 계약서에서 법적 쟁점(issue) 감지
        List<Issue> issues = openAiClient.detectUnfairClauses(contract.getOcrText());
        log.info("AI가 감지한 법적 이슈: {} 건", issues.size());
        
        Map<Issue, List<LawInfo>> issueLawMap = new HashMap<>();

        for (Issue issue : issues) {
            // 2. 각 쟁점과 관련된 법률 목록을 API로 검색
            List<LawInfo> laws = lawApiClient.searchRelatedLaws(issue.getType(), contract);
            log.info("'{}' 이슈 관련 법률 {}건 검색됨", issue.getType(), laws.size());

            laws.forEach(law -> {
                try {
                    // 3. 법률의 상세 본문 내용을 웹 스크래핑이 아닌 API로 조회
                    String lawContent = lawApiClient.fetchLawDetailByApi(law.getLawSerialNumber());

                    if (!StringUtils.hasText(lawContent)) {
                        log.warn("법률 '{}'의 상세 내용을 API로 가져오지 못했습니다. 분석을 건너뜁니다.", law.getLawName());
                        return; // 내용이 없으면 이 법률은 처리하지 않고 다음으로 넘어감
                    }

                    // 4. AI를 통해 법률 이름 번역 및 본문 요약/번역
                    law.setTranslatedLawName(openAiClient.translateText(law.getLawName(), targetLanguage));
                    law.setTranslatedSummary(openAiClient.summarizeAndTranslate(lawContent, targetLanguage));
                    law.setContract(contract);
                    log.info("성공적으로 법률 '{}' 처리 완료.", law.getLawName());

                } catch (Exception e) {
                    log.error("법률 상세 정보 처리 중 예외 발생: '{}', {}", law.getLawName(), e.getMessage());
                }
            });

            try {
                // 요약/번역이 성공적으로 완료된 법률 정보만 필터링
                List<LawInfo> validLaws = laws.stream()
                        .filter(l -> StringUtils.hasText(l.getTranslatedSummary()))
                        .collect(Collectors.toList());
                
                if (!validLaws.isEmpty()) {
                    lawInfoRepository.saveAll(validLaws);
                }
                issueLawMap.put(issue, validLaws);

            } catch (DataIntegrityViolationException e) {
                log.error("DB 저장 실패: {}", e.getMessage(), e);
                throw new RuntimeException("법령 정보 저장에 실패했습니다.", e);
            }
        }

        List<LawInfoDTO> allLawInfos = issueLawMap.values().stream()
                .flatMap(List::stream)
                .map(LawInfoDTO::new)
                .collect(Collectors.toList());

        return new LawAnalyzeDto(contract.getContractId(), issues, allLawInfos);
    }

    public List<LawInfo> getLawsByContractId(Long contractId) {
        return lawInfoRepository.findByContractContractId(contractId);
    }

    public LawInfo getLawById(Long lawInfoId) {
        return lawInfoRepository.findById(lawInfoId)
                .orElseThrow(() -> new IllegalArgumentException("법률 정보를 찾을 수 없습니다."));
    }
}
