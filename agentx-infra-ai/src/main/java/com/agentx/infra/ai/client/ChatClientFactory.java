package com.agentx.infra.ai.client;

import com.agentx.common.api.ErrorCode;
import com.agentx.common.exception.BizException;
import com.agentx.infra.ai.model.ModelConfig;
import com.agentx.infra.ai.model.ModelConfigService;
import com.agentx.infra.ai.model.ProviderType;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * ChatClient 工厂 —— 平台唯一的模型出口（设计文档 §4.2）。
 * <p>
 * 业务模块一律经此获取 {@link ChatClient}，禁止直接构建 ChatModel；
 * 供应商差异由 {@link ChatModelProvider} 策略承担，Spring 注入自动发现。
 * 实例按配置 ID 缓存（构建含 HTTP 客户端初始化，非无成本），
 * 配置变更经 {@link ModelConfigChangedEvent} 精确驱逐。
 */
@Component
public class ChatClientFactory {

    private final ModelConfigService modelConfigService;
    private final Map<ProviderType, ChatModelProvider> providers;
    private final Cache<UUID, ChatClient> cache = Caffeine.newBuilder()
            .maximumSize(64)
            .expireAfterAccess(Duration.ofHours(12))
            .build();

    public ChatClientFactory(ModelConfigService modelConfigService,
                             List<ChatModelProvider> providerList) {
        this.modelConfigService = modelConfigService;
        Map<ProviderType, ChatModelProvider> map = new EnumMap<>(ProviderType.class);
        providerList.forEach(p -> map.put(p.type(), p));
        this.providers = map;
    }

    /** 按配置 ID 获取（会话绑定了特定模型时用）。 */
    public ChatClient get(UUID configId) {
        return cache.get(configId, id -> build(modelConfigService.getEnabled(id)));
    }

    /** 获取默认 CHAT 模型的 client。 */
    public ChatClient getDefault() {
        ModelConfig config = modelConfigService.getDefaultChat();
        return cache.get(config.getId(), id -> build(config));
    }

    private ChatClient build(ModelConfig config) {
        ChatModelProvider provider = providers.get(config.getProviderType());
        if (provider == null) {
            throw new BizException(ErrorCode.INTERNAL_ERROR,
                    "无可用的供应商策略: " + config.getProviderType());
        }
        String apiKey = modelConfigService.decryptedApiKey(config);
        // 工具承载依赖 ChatModel.getOptions() 返回 ToolCallingChatOptions（请求装配以其
        // mutate 结果为基底拷入 toolCallbacks），官方 provider 模型均满足；自定义
        // ChatModel（含测试 stub）必须覆写 getOptions()，否则工具被静默丢弃。
        return ChatClient.builder(provider.build(config, apiKey)).build();
    }

    @EventListener
    public void onModelConfigChanged(ModelConfigChangedEvent event) {
        cache.invalidate(event.configId());
    }
}
