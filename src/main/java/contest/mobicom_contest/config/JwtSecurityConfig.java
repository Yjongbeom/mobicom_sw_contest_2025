package contest.mobicom_contest.config;

import contest.mobicom_contest.jwt.JwtAuthenticationFilter;
import contest.mobicom_contest.jwt.JwtTokenProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JwtSecurityConfig {
    private final JwtTokenProvider jwtTokenProvider;

    public JwtSecurityConfig(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter() {
        return new JwtAuthenticationFilter(jwtTokenProvider);
    }
}
