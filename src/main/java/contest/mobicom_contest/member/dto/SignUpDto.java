package contest.mobicom_contest.member.dto;


import contest.mobicom_contest.member.model.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@NoArgsConstructor
@Schema(description = "회원가입 요청")
public class SignUpDto {
    @NotBlank(message = "Username cannot be blank")
    @Schema(description = "사용자 이름")
    private String username;

    @NotBlank(message = "Password cannot be blank")
    @Schema(description = "비밀번호")
    private String password;

    private String nationality;
    private String language;
    private String workLocation;
    private Integer experienceYears;
    private Role role;
}
