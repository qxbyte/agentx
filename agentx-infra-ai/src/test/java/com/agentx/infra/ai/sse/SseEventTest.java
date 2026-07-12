package com.agentx.infra.ai.sse;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.assertThat;

class SseEventTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void payloadIsFlatWithTypeFirst() {
        String json = objectMapper.writeValueAsString(SseEvent.textDelta("你好").toPayload());
        assertThat(json).isEqualTo("{\"type\":\"text-delta\",\"delta\":\"你好\"}");
    }

    @Test
    void doneCarriesUsage() {
        String json = objectMapper.writeValueAsString(SseEvent.done(10, 20, null).toPayload());
        assertThat(json).contains("\"type\":\"done\"")
                .contains("\"promptTokens\":10")
                .contains("\"completionTokens\":20")
                .contains("\"finishReason\":\"stop\"");
    }

    @Test
    void errorFrameHasCodeAndMessage() {
        String json = objectMapper.writeValueAsString(SseEvent.error("50000", "模型调用失败").toPayload());
        assertThat(json).contains("\"code\":\"50000\"").contains("模型调用失败");
    }
}
