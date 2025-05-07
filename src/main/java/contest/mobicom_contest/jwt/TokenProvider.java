package contest.mobicom_contest.jwt;

import org.springframework.security.core.Authentication;

public interface TokenProvider {
    JwtToken generateToken(Authentication authentication);
    Authentication getAuthentication(String accessToken);
    boolean validateToken(String token);
}
