package com.agentx.infra.ai.client;

import com.agentx.infra.ai.model.ModelConfig;
import com.agentx.infra.ai.model.ProviderType;
import org.springframework.ai.embedding.EmbeddingModel;

/** Embedding 模型构建策略（SPI），与 {@link ChatModelProvider} 同构。 */
public interface EmbeddingModelProvider {

    ProviderType type();

    EmbeddingModel build(ModelConfig config, String apiKey);
}
