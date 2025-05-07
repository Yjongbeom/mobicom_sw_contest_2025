package contest.mobicom_contest.member.dto;

import lombok.*;

@Getter
@ToString
@NoArgsConstructor
public class MemberDto {
    private String nationality;
    private String language;
    private String workLocation;
    private Integer experienceYears;

    public MemberDto(String nationality, String language, String workLocation, Integer experienceYears) {
        this.nationality = nationality;
        this.language = language;
        this.workLocation = workLocation;
        this.experienceYears = experienceYears;
    }
}
