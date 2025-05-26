package contest.mobicom_contest.member.dto;

import contest.mobicom_contest.jwt.JwtToken;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CustomAuthResponseDto {
    private Long memberId;
    private JwtToken jwtToken;
}
