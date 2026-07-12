package com.agentx.infra.ai.client.provider;

import com.agentx.common.api.ErrorCode;
import com.agentx.common.exception.BizException;
import com.agentx.infra.ai.client.EmbeddingModelProvider;
import com.agentx.infra.ai.model.ModelConfig;
import com.agentx.infra.ai.model.ProviderType;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class OpenAiCompatibleEmbeddingModelProvider implements EmbeddingModelProvider {

    @Override
    public ProviderType type() {
        return ProviderType.OPENAI_COMPATIBLE;
    }

    @Override
    public EmbeddingModel build(ModelConfig config, String apiKey) {
        if (!StringUtils.hasText(config.getBaseUrl())) {
            throw new BizException(ErrorCode.BAD_REQUEST,
                    "OPENAI_COMPATIBLE embedding 必须配置 baseUrl");
        }
        return new OpenAiCompatibleEmbeddingModel(config.getBaseUrl(), apiKey, config.getModelName());
    }
}
