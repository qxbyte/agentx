package com.agentx.chat;

import com.agentx.auth.domain.SysUserRepository;
import com.agentx.auth.security.AuthPrincipal;
import com.agentx.chat.domain.ChatMessageRepository;
import com.agentx.infra.ai.client.ChatClientFactory;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import reactor.core.publisher.Flux;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ChatStreamFlowTest {

    @Autowired MockMvc mvc;
    @Autowired SysUserRepository userRepository;
    @Autowired ChatMessageRepository messageRepository;
    @MockitoBean ChatClientFactory chatClientFactory;

    /** 预置三段 delta 的 stub 模型，覆盖流式聚合与落库。 */
    static class StubChatModel implements ChatModel {
        @Override
        public ChatResponse call(Prompt prompt) {
            return new ChatResponse(List.of(new Generation(new AssistantMessage("你好，世界"))));
        }

        @Override
        public Flux<ChatResponse> stream(Prompt prompt) {
            return Flux.just(chunk("你好"), chunk("，"), chunk("世界"));
        }

        private static ChatResponse chunk(String text) {
            return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
        }
    }

    @Test
    void streamPersistsBothMessagesAndEmitsEnvelopes() throws Exception {
        when(chatClientFactory.getDefault())
                .thenReturn(ChatClient.builder(new StubChatModel()).build());
        var admin = userRepository.findByUsername("admin").orElseThrow();
        var auth = new UsernamePasswordAuthenticationToken(
                new AuthPrincipal(admin.getId(), admin.getUsername(), admin.getRole()),
                null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

        MvcResult started = mvc.perform(post("/api/v1/chat/stream")
                        .with(authentication(auth))
                        .contentType(APPLICATION_JSON)
                        .content("{\"content\":\"打个招呼\"}"))
                .andExpect(request().asyncStarted())
                .andReturn();

        String body = mvc.perform(asyncDispatch(started))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("\"type\":\"meta\"")
                .contains("\"type\":\"text-delta\"")
                .contains("\"type\":\"done\"");

        // 双轨业务侧：USER + ASSISTANT 两条消息落库，ASSISTANT 聚合为完整文本
        String conversationId = body.substring(body.indexOf("conversationId\":\"") + 17,
                body.indexOf("conversationId\":\"") + 17 + 36);
        var messages = messageRepository.findByConversationIdOrderByCreatedAtAsc(
                java.util.UUID.fromString(conversationId));
        assertThat(messages).hasSize(2);
        assertThat(messages.get(1).getContent()).isEqualTo("你好，世界");
    }
}
