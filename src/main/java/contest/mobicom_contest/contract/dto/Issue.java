package contest.mobicom_contest.contract.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Embeddable
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Issue {
    @JsonProperty("type")
    private String type;

    @JsonProperty("reason")
    private String reason;

    @JsonProperty("evidence")
    private String evidence;
}
