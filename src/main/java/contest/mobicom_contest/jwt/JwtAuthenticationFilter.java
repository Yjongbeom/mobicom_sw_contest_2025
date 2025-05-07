package contest.mobicom_contest.jwt;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.GenericFilterBean;

import java.io.IOException;

@Component
public class JwtAuthenticationFilter extends GenericFilterBean {
    private final JwtTokenProvider tokenProvider;

    public JwtAuthenticationFilter(JwtTokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) req;
        String requestURI = request.getRequestURI();


        if (requestURI.startsWith("/api/register") ||
                requestURI.startsWith("/api/login") ||
                requestURI.startsWith("/swagger-ui") ||
                requestURI.startsWith("/v3/api-docs") ||
                requestURI.startsWith("/swagger-resources") ||
                requestURI.startsWith("/webjars") ||
                requestURI.startsWith("/error")){
            chain.doFilter(req, res);
            return;
        }


        String token = resolveToken((HttpServletRequest) req);

        if(token != null && tokenProvider.validateToken(token)) {
            Authentication auth = tokenProvider.getAuthentication(token);
            SecurityContextHolder.getContext().setAuthentication(auth);
        }
        chain.doFilter(req, res);
    }

    private String resolveToken(HttpServletRequest req) {
        String bearerToken = req.getHeader("Authorization");
        if(StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
