package com.agentx.infra.ai.client;

import com.agentx.common.api.ErrorCode;
import com.agentx.common.exception.BizException;
import com.agentx.infra.ai.model.ModelConfig;
import com.agentx.infra.ai.model.ModelConfigService;
import com.agentx.infra.ai.model.ModelType;
import com.agentx.infra.ai.model.ProviderType;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Embedding 模型工厂，与 {@link ChatClientFactory} 同构（缓存 + 事件驱逐）。 */
@Component
public class EmbeddingModelFactory {

    private final ModelConfigService modelConfigService;
    private final Map<ProviderType, EmbeddingModelProvider> providers;
    private final Cache<UUID, EmbeddingModel> cache = Caffeine.newBuilder()
            .maximumSize(16)
            .expireAfterAccess(Duration.ofHours(12))
            .build();

    public EmbeddingModelFactory(ModelConfigService modelConfigService,
                                 List<EmbeddingModelProvider> providerList) {
        this.modelConfigService = modelConfigService;
        Map<ProviderType, EmbeddingModelProvider> map = new EnumMap<>(ProviderType.class);
        providerList.forEach(p -> map.put(p.type(), p));
        this.providers = map;
    }

    public EmbeddingModel get(UUID configId) {
        return cache.get(configId, id -> build(modelConfigService.getEnabled(id)));
    }

    public EmbeddingModel getDefault() {
        ModelConfig config = modelConfigService.getDefaultOf(ModelType.EMBEDDING);
        return cache.get(config.getId(), id -> build(config));
    }

    private EmbeddingModel build(ModelConfig config) {
        if (config.getType() != ModelType.EMBEDDING) {
            throw new BizException(ErrorCode.BAD_REQUEST,
                    "模型配置不是 EMBEDDING 类型: " + config.getName());
        }
        EmbeddingModelProvider provider = providers.get(config.getProviderType());
        if (provider == null) {
            throw new BizException(ErrorCode.INTERNAL_ERROR,
                    "无可用的 embedding 供应商策略: " + config.getProviderType());
        }
        return provider.build(config, modelConfigService.decryptedApiKey(config));
    }

    @EventListener
    public void onModelConfigChanged(ModelConfigChangedEvent event) {
        cache.invalidate(event.configId());
    }
}
