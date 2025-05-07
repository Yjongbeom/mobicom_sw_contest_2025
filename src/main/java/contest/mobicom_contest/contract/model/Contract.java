package contest.mobicom_contest.contract.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import contest.mobicom_contest.contract.dto.Issue;
import contest.mobicom_contest.law.model.LawInfo;
import contest.mobicom_contest.member.model.Member;
import jakarta.persistence.*;
import lombok.*;


import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "Contract")
@Getter
@Builder
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Contract {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "contract_id")
    private Long contractId;

    @Setter
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    @JsonBackReference
    private Member member;

    @Column(name = "original_image_path")
    private String originalImagePath;

    @Column(name = "translated_image_path")
    private String translatedImagePath;

    @Column(columnDefinition = "TEXT")
    private String ocrText;

    @Column(columnDefinition = "TEXT")
    private String issuesJson;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "contract_issues", joinColumns = @JoinColumn(name = "contract_id"))
    private List<Issue> issues = new ArrayList<>();

    @OneToMany(mappedBy = "contract", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<LawInfo> lawInfos = new ArrayList<>();

    @Transient
    private String translatedText;

}