package com.agentx.infra.ai.client.provider;

import com.agentx.infra.ai.client.ChatModelProvider;
import com.agentx.infra.ai.model.ModelConfig;
import com.agentx.infra.ai.model.ProviderType;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class OllamaChatModelProvider implements ChatModelProvider {

    private static final String DEFAULT_BASE_URL = "http://localhost:11434";

    @Override
    public ProviderType type() {
        return ProviderType.OLLAMA;
    }

    @Override
    public ChatModel build(ModelConfig config, String apiKey) {
        String baseUrl = StringUtils.hasText(config.getBaseUrl())
                ? config.getBaseUrl() : DEFAULT_BASE_URL;
        OllamaApi api = OllamaApi.builder().baseUrl(baseUrl).build();
        OllamaChatOptions.Builder options = OllamaChatOptions.builder();
        options.model(config.getModelName());
        return OllamaChatModel.builder()
                .ollamaApi(api)
                .options(options.build())
                .build();
    }
}
