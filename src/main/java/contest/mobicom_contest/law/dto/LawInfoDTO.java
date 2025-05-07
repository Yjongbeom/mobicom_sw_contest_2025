package contest.mobicom_contest.law.dto;

import contest.mobicom_contest.law.model.LawInfo;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class LawInfoDTO {
    private String lawName;
    private String translatedLawName;
    private String translatedSummary;
    private String referenceNumber;
    private String sourceLink;

    public LawInfoDTO(LawInfo lawInfo) {
        this.lawName = lawInfo.getLawName();
        this.translatedLawName = lawInfo.getTranslatedLawName();
        this.translatedSummary = lawInfo.getTranslatedSummary();
        this.referenceNumber = lawInfo.getReferenceNumber();
        this.sourceLink = "https://www.law.go.kr" + lawInfo.getDetailUrl();
    }
}


