package contest.mobicom_contest.contract.dto;

import contest.mobicom_contest.contract.model.Contract;
import lombok.Data;

@Data
public class ContractResponseDTO {
    private Long contractId;
    private Long memberId;
    private String originalImageUrl;
    private String translatedImageUrl;

    public ContractResponseDTO(Contract contract) {
        this.contractId = contract.getContractId();
        this.memberId = contract.getMember().getId();
        this.originalImageUrl = contract.getOriginalImagePath();
        this.translatedImageUrl = contract.getTranslatedImagePath();
    }
}