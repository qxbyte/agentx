package com.agentx.auth.jwt;

import com.agentx.auth.domain.SysUser;
import com.agentx.common.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class JwtServiceTest {
    private JwtService jwtService;
    private SysUser user;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(new JwtProperties(
                "test-secret-0123456789abcdef", Duration.ofHours(2), Duration.ofDays(7)));
        user = new SysUser();
        user.setId(UUID.randomUUID());
        user.setUsername("alice");
        user.setRole("ADMIN");
    }

    @Test
    void issueAndVerifyAccessToken() {
        String token = jwtService.issueAccess(user);
        JwtService.DecodedJwt d = jwtService.verify(token);
        assertThat(d.userId()).isEqualTo(user.getId());
        assertThat(d.username()).isEqualTo("alice");
        assertThat(d.role()).isEqualTo("ADMIN");
        assertThat(d.tokenType()).isEqualTo("access");
    }

    @Test
    void refreshTokenHasRefreshType() {
        assertThat(jwtService.verify(jwtService.issueRefresh(user)).tokenType()).isEqualTo("refresh");
    }

    @Test
    void garbageTokenRejected() {
        assertThatThrownBy(() -> jwtService.verify("not.a.jwt"))
                .isInstanceOf(BizException.class);
    }
}
