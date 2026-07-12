package com.agentx.infra.ai.client.provider;

import com.agentx.infra.ai.client.EmbeddingModelProvider;
import com.agentx.infra.ai.model.ModelConfig;
import com.agentx.infra.ai.model.ProviderType;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaEmbeddingOptions;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class OllamaEmbeddingModelProvider implements EmbeddingModelProvider {

    private static final String DEFAULT_BASE_URL = "http://localhost:11434";

    @Override
    public ProviderType type() {
        return ProviderType.OLLAMA;
    }

    @Override
    public EmbeddingModel build(ModelConfig config, String apiKey) {
        String baseUrl = StringUtils.hasText(config.getBaseUrl())
                ? config.getBaseUrl() : DEFAULT_BASE_URL;
        return OllamaEmbeddingModel.builder()
                .ollamaApi(OllamaApi.builder().baseUrl(baseUrl).build())
                .options(OllamaEmbeddingOptions.builder().model(config.getModelName()).build())
                .build();
    }
}
