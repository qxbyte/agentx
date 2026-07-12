package com.agentx.infra.ai.crypto;

import org.junit.jupiter.api.Test;
import java.util.Base64;
import static org.assertj.core.api.Assertions.assertThat;

class ApiKeyCryptoTest {
    private final ApiKeyCrypto crypto = new ApiKeyCrypto(
            Base64.getEncoder().encodeToString(new byte[32]));

    @Test
    void roundTrip() {
        String enc = crypto.encrypt("sk-abc123");
        assertThat(enc).isNotEqualTo("sk-abc123");
        assertThat(crypto.decrypt(enc)).isEqualTo("sk-abc123");
    }

    @Test
    void differentIvEachTime() {
        assertThat(crypto.encrypt("same")).isNotEqualTo(crypto.encrypt("same"));
    }
}
