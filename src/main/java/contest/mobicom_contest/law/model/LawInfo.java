package contest.mobicom_contest.law.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.*;
import contest.mobicom_contest.contract.model.Contract;

@Entity
@Table(name = "LawInfo")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Setter
public class LawInfo {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long lawInfoId;

    @Column(nullable = false)
    private String lawName;

    @Column(nullable = false)
    private String referenceNumber;

    @Column(nullable = false)
    private String detailUrl;

    @Column(nullable = false)
    private String translatedLawName;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String translatedSummary;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contract_id")
    @JsonBackReference
    private Contract contract;
}