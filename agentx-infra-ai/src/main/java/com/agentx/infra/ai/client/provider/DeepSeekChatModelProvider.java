package com.agentx.infra.ai.client.provider;

import com.agentx.infra.ai.client.ChatModelProvider;
import com.agentx.infra.ai.model.ModelConfig;
import com.agentx.infra.ai.model.ProviderType;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.deepseek.api.DeepSeekApi;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class DeepSeekChatModelProvider implements ChatModelProvider {

    @Override
    public ProviderType type() {
        return ProviderType.DEEPSEEK;
    }

    @Override
    public ChatModel build(ModelConfig config, String apiKey) {
        DeepSeekApi.Builder api = DeepSeekApi.builder().apiKey(apiKey);
        if (StringUtils.hasText(config.getBaseUrl())) {
            api.baseUrl(config.getBaseUrl());
        }
        DeepSeekChatOptions.Builder options = DeepSeekChatOptions.builder();
        options.model(config.getModelName());
        return DeepSeekChatModel.builder()
                .deepSeekApi(api.build())
                .options(options.build())
                .build();
    }
}
