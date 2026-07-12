package com.agentx.infra.ai.client;

import com.agentx.infra.ai.client.provider.OllamaChatModelProvider;
import com.agentx.infra.ai.model.ModelConfig;
import com.agentx.infra.ai.model.ModelConfigService;
import com.agentx.infra.ai.model.ModelType;
import com.agentx.infra.ai.model.ProviderType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChatClientFactoryTest {

    private ModelConfigService modelConfigService;
    private ChatClientFactory factory;
    private ModelConfig ollamaConfig;

    @BeforeEach
    void setUp() {
        modelConfigService = mock(ModelConfigService.class);
        factory = new ChatClientFactory(modelConfigService, List.of(new OllamaChatModelProvider()));
        ollamaConfig = new ModelConfig();
        ollamaConfig.setId(UUID.randomUUID());
        ollamaConfig.setName("local-qwen");
        ollamaConfig.setProviderType(ProviderType.OLLAMA);
        ollamaConfig.setModelName("qwen3");
        ollamaConfig.setType(ModelType.CHAT);
        ollamaConfig.setEnabled(true);
        when(modelConfigService.getEnabled(ollamaConfig.getId())).thenReturn(ollamaConfig);
        when(modelConfigService.decryptedApiKey(any())).thenReturn(null);
    }

    @Test
    void buildsOllamaClientWithoutApiKey() {
        assertThat(factory.get(ollamaConfig.getId())).isInstanceOf(ChatClient.class);
    }

    @Test
    void cachesInstancePerConfigId() {
        assertThat(factory.get(ollamaConfig.getId())).isSameAs(factory.get(ollamaConfig.getId()));
    }

    @Test
    void configChangeEvictsCache() {
        ChatClient first = factory.get(ollamaConfig.getId());
        factory.onModelConfigChanged(new ModelConfigChangedEvent(ollamaConfig.getId()));
        assertThat(factory.get(ollamaConfig.getId())).isNotSameAs(first);
    }
}
