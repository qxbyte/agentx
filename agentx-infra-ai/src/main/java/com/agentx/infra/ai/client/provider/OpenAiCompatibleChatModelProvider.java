package com.agentx.infra.ai.client.provider;

import com.agentx.common.api.ErrorCode;
import com.agentx.common.exception.BizException;
import com.agentx.infra.ai.client.ChatModelProvider;
import com.agentx.infra.ai.model.ModelConfig;
import com.agentx.infra.ai.model.ProviderType;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.deepseek.api.DeepSeekApi;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * OpenAI 兼容端点策略：通义千问 / 智谱 / Moonshot / vLLM 等一切暴露
 * {@code /v1/chat/completions} 协议的服务。
 * <p>
 * 复用 spring-ai-deepseek 的协议客户端而非 spring-ai-openai：后者 2.0 起绑定
 * openai-java 官方 SDK（独立 OkHttp 栈），前者基于 Spring RestClient/WebClient，
 * baseUrl 与 completions 路径可配，且额外支持 reasoning_content 解析——
 * 统一 HTTP 技术栈、减少外部 SDK 依赖面。
 */
@Component
public class OpenAiCompatibleChatModelProvider implements ChatModelProvider {

    @Override
    public ProviderType type() {
        return ProviderType.OPENAI_COMPATIBLE;
    }

    @Override
    public ChatModel build(ModelConfig config, String apiKey) {
        if (!StringUtils.hasText(config.getBaseUrl())) {
            throw new BizException(ErrorCode.BAD_REQUEST,
                    "OPENAI_COMPATIBLE 供应商必须配置 baseUrl");
        }
        DeepSeekApi api = DeepSeekApi.builder()
                .apiKey(apiKey)
                .baseUrl(config.getBaseUrl())
                .completionsPath("/chat/completions")
                .build();
        DeepSeekChatOptions.Builder options = DeepSeekChatOptions.builder();
        options.model(config.getModelName());
        return DeepSeekChatModel.builder()
                .deepSeekApi(api)
                .options(options.build())
                .build();
    }

    /**
     * 多模态通道：DeepSeek 协议客户端的消息 content 是纯 String（结构上无法携带图片），
     * 带图轮次改用 spring-ai-openai（openai-java SDK），其 content 数组原生支持 image_url。
     */
    @Override
    public ChatModel buildVision(ModelConfig config, String apiKey) {
        // spring-ai-openai 自带 OkHttp 传输实现（openai-java 官方 okhttp 客户端为可选依赖未随包）
        com.openai.client.OpenAIClient client = new com.openai.client.OpenAIClientImpl(
                com.openai.core.ClientOptions.builder()
                        .httpClient(org.springframework.ai.openai.http.okhttp.SpringAiOpenAiHttpClient
                                .builder().build())
                        .baseUrl(config.getBaseUrl())
                        // apiKey(String) 不落 credential 槽位，须显式给 Bearer 凭证
                        .credential(com.openai.credential.BearerTokenCredential.create(apiKey))
                        .build());
        return org.springframework.ai.openai.OpenAiChatModel.builder()
                .openAiClient(client)
                // 流式走 async 客户端：不显式传入时模型会自建（无凭证 → IllegalStateException）
                .openAiClientAsync(client.async())
                .options(org.springframework.ai.openai.OpenAiChatOptions.builder()
                        .model(config.getModelName())
                        .build())
                .build();
    }
}
