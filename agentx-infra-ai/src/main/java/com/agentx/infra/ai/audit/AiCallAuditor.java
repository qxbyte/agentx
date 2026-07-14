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

    /** CHAT 调用审计（对话主链路）。 */
    @Async
    public void record(UUID userId, UUID conversationId, String modelName,
                       long promptTokens, long completionTokens, long latencyMs,
                       CallStatus status) {
        save("CHAT", userId, conversationId, modelName, promptTokens, completionTokens, latencyMs, status);
    }

    /**
     * EMBEDDING 调用审计（查询向量化 / 入库 / 重嵌）。与 CHAT 分开计量——即便同厂商，
     * 其 chat 与 embedding 也各算各。embedding 无 completion，token 记在 promptTokens；
     * userId/conversationId 常不可得（入库等非请求上下文），允许为空。
     */
    @Async
    public void recordEmbedding(String modelName, long totalTokens, long latencyMs, CallStatus status) {
        save("EMBEDDING", null, null, modelName, totalTokens, 0, latencyMs, status);
    }

    /** RERANK 调用审计（检索精排）。与 CHAT/EMBEDDING 分开计量。 */
    @Async
    public void recordRerank(String modelName, long totalTokens, long latencyMs, CallStatus status) {
        save("RERANK", null, null, modelName, totalTokens, 0, latencyMs, status);
    }

    private void save(String modelType, UUID userId, UUID conversationId, String modelName,
                      long promptTokens, long completionTokens, long latencyMs, CallStatus status) {
        try {
            AiCallLog log = new AiCallLog();
            log.setId(UuidV7.next());
            log.setModelType(modelType);
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
