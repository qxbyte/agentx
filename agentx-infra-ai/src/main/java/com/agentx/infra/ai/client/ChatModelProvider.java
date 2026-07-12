package com.agentx.infra.ai.client;

import com.agentx.infra.ai.model.ModelConfig;
import com.agentx.infra.ai.model.ProviderType;
import org.springframework.ai.chat.model.ChatModel;

/**
 * 模型供应商构建策略（SPI）。
 * <p>
 * 新增供应商 = 新增一个实现类并声明 @Component，{@link ChatClientFactory}
 * 通过 Spring 注入自动发现，工厂本身零改动。
 */
public interface ChatModelProvider {

    /** 本策略服务的供应商类型。 */
    ProviderType type();

    /**
     * 按配置构建底层 ChatModel。
     *
     * @param config 模型配置（baseUrl / modelName 等）
     * @param apiKey 已解密的 api-key；无需鉴权的供应商（如 Ollama）可为 null
     */
    ChatModel build(ModelConfig config, String apiKey);
}
