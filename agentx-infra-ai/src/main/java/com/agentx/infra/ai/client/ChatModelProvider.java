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

    /**
     * 构建支持多模态（图片 Media）的 ChatModel；不支持的供应商返回 null。
     * <p>
     * 默认文本客户端与多模态客户端分离：文本轮次保留 reasoning_content 解析
     * 等协议特性，带图轮次切换到本方法构建的客户端。
     */
    default ChatModel buildVision(ModelConfig config, String apiKey) {
        return null;
    }
}
