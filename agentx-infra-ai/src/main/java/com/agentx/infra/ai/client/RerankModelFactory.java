package com.agentx.infra.ai.client;

import com.agentx.infra.ai.audit.AiCallAuditor;
import com.agentx.infra.ai.client.provider.DashScopeRerankModel;
import com.agentx.infra.ai.model.ModelConfig;
import com.agentx.infra.ai.model.ModelConfigService;
import com.agentx.infra.ai.model.ModelType;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Rerank 模型工厂，与 {@link EmbeddingModelFactory} 同构（缓存 + 事件驱逐 + 审计装饰）。
 * <p>
 * rerank 是检索质量层的可选一环：{@link #findDefault()} 返回默认启用的 RERANK 模型，
 * 无则返回空——「配置并启用默认 RERANK 模型 = 开精排；否则关（退回 RRF-only）」，
 * 模型管理页的启用开关即精排开关。
 * <p>
 * 当前内置 DashScope gte-rerank 客户端；指向兼容其请求/响应形态的服务只需改 baseUrl/model。
 */
@Component
@RequiredArgsConstructor
public class RerankModelFactory {

    private final ModelConfigService modelConfigService;
    private final AiCallAuditor auditor;
    private final Cache<UUID, RerankModel> cache = Caffeine.newBuilder()
            .maximumSize(8)
            .expireAfterAccess(Duration.ofHours(12))
            .build();

    /** 默认启用的 rerank 模型（精排开关）；未配置则空，检索退回 RRF-only。 */
    public Optional<RerankModel> findDefault() {
        return modelConfigService.findDefaultOf(ModelType.RERANK)
                .map(config -> cache.get(config.getId(), id -> build(config)));
    }

    private RerankModel build(ModelConfig config) {
        RerankModel model = new DashScopeRerankModel(
                config.getBaseUrl(), modelConfigService.decryptedApiKey(config), config.getModelName());
        return new AuditingRerankModel(model, config.getModelName(), auditor);
    }

    @EventListener
    public void onModelConfigChanged(ModelConfigChangedEvent event) {
        cache.invalidate(event.configId());
    }
}
