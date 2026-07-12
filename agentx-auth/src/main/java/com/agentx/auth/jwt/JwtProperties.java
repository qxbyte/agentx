package com.agentx.auth.jwt;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.time.Duration;

@ConfigurationProperties(prefix = "agentx.jwt")
public record JwtProperties(String secret, Duration accessTtl, Duration refreshTtl) {
    public JwtProperties {
        if (accessTtl == null) accessTtl = Duration.ofHours(2);
        if (refreshTtl == null) refreshTtl = Duration.ofDays(7);
    }
}
