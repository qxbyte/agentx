package com.agentx.infra.ai.audit;

import com.agentx.common.util.UuidV7;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import java.util.UUID;

/**
 * 模型调用审计：token 用量 / 延迟 / 状态落 ai_call_log。
 * 异步写入——审计失败绝不影响业务链路。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiCallAuditor {
    private final AiCallLogRepository repository;

    public enum CallStatus { OK, ERROR }

    @Async
    public void record(UUID userId, UUID conversationId, String modelName,
                       long promptTokens, long completionTokens, long latencyMs,
                       CallStatus status) {
        try {
            AiCallLog log = new AiCallLog();
            log.setId(UuidV7.next());
            log.setUserId(userId);
            log.setConversationId(conversationId);
            log.setModelName(modelName);
            log.setPromptTokens(promptTokens);
            log.setCompletionTokens(completionTokens);
            log.setLatencyMs(latencyMs);
            log.setStatus(status.name());
            repository.save(log);
        } catch (Exception e) {
            AiCallAuditor.log.warn("审计日志写入失败: {}", e.getMessage());
        }
    }
}
