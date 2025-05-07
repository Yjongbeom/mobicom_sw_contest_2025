package contest.mobicom_contest.law.dto;

import contest.mobicom_contest.contract.dto.Issue;
import lombok.Data;

import java.util.List;

@Data
public class LawAnalyzeDto {
    private Long contractId;
    private List<Issue> issues;
    private List<LawInfoDTO> laws;

    public LawAnalyzeDto(Long contractId, List<Issue> issues, List<LawInfoDTO> laws) {
        this.contractId = contractId;
        this.issues = issues;
        this.laws = laws;
    }
}