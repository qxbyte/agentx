package com.agentx.auth.jwt;

import com.agentx.auth.domain.SysUser;
import com.agentx.common.api.ErrorCode;
import com.agentx.common.exception.BizException;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.UUID;

@Service
public class JwtService {
    private final JwtProperties props;
    private final Algorithm algorithm;

    public JwtService(JwtProperties props) {
        this.props = props;
        this.algorithm = Algorithm.HMAC256(props.secret());
    }

    public record DecodedJwt(UUID userId, String username, String role, String tokenType) {}

    public String issueAccess(SysUser user) {
        return issue(user, "access", props.accessTtl().toSeconds());
    }

    public String issueRefresh(SysUser user) {
        return issue(user, "refresh", props.refreshTtl().toSeconds());
    }

    private String issue(SysUser user, String type, long ttlSeconds) {
        Instant now = Instant.now();
        return JWT.create()
                .withSubject(user.getId().toString())
                .withClaim("username", user.getUsername())
                .withClaim("role", user.getRole())
                .withClaim("type", type)
                .withIssuedAt(now)
                .withExpiresAt(now.plusSeconds(ttlSeconds))
                .sign(algorithm);
    }

    public DecodedJwt verify(String token) {
        try {
            DecodedJWT jwt = JWT.require(algorithm).build().verify(token);
            return new DecodedJwt(
                    UUID.fromString(jwt.getSubject()),
                    jwt.getClaim("username").asString(),
                    jwt.getClaim("role").asString(),
                    jwt.getClaim("type").asString());
        } catch (JWTVerificationException e) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "无效或过期的令牌");
        }
    }
}
