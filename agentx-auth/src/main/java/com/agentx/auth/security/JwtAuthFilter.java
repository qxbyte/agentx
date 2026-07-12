package com.agentx.auth.security;

import com.agentx.auth.jwt.JwtService;
import com.agentx.common.exception.BizException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {
    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            try {
                JwtService.DecodedJwt d = jwtService.verify(header.substring(7));
                if ("access".equals(d.tokenType())) {
                    AuthPrincipal principal = new AuthPrincipal(d.userId(), d.username(), d.role());
                    UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                            principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + d.role())));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            } catch (BizException ignored) {
                // 无效 token 视为匿名，由授权规则拦截
            }
        }
        chain.doFilter(request, response);
    }
}
