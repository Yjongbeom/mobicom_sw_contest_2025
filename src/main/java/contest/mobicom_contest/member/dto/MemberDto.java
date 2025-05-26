package contest.mobicom_contest.member.dto;

import lombok.*;

@Getter
@ToString
@NoArgsConstructor
public class MemberDto {
    private String username;
    private String nationality;
    private String language;
    private String workLocation;
    private Integer experienceYears;
    private String phone;
    private String nickname;

    public MemberDto(String username, String nationality, String language, String workLocation, Integer experienceYears, String phone, String nickname) {
        this.username = username;
        this.nationality = nationality;
        this.language = language;
        this.workLocation = workLocation;
        this.experienceYears = experienceYears;
        this.phone = phone;
        this.nickname = nickname;
    }

    public MemberDto(String nationality, String language, String workLocation, Integer experienceYears, String phone, String nickname) {
        this.nationality = nationality;
        this.language = language;
        this.workLocation = workLocation;
        this.experienceYears = experienceYears;
        this.phone = phone;
        this.nickname = nickname;
    }
}
