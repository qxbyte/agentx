package com.agentx.skill;

import com.agentx.auth.domain.SysUserRepository;
import com.agentx.auth.security.AuthPrincipal;
import com.agentx.chat.domain.ChatMessageRepository;
import com.agentx.infra.ai.client.ChatClientFactory;
import com.agentx.skill.store.SkillFile;
import com.agentx.skill.store.SkillFileStore;
import org.junit.jupiter.api.AfterEach;
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
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 端到端：/name 命令经聊天流展开进模型 prompt，业务轨保留用户原文（双轨）。 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SkillExpansionFlowTest {

    @Autowired MockMvc mvc;
    @Autowired SysUserRepository userRepository;
    @Autowired SkillFileStore skillStore;
    @Autowired ChatMessageRepository messageRepository;
    @MockitoBean ChatClientFactory chatClientFactory;

    private String skillName;

    /** 捕获送入模型的最终 Prompt，供展开断言。 */
    static class CapturingChatModel implements ChatModel {
        final AtomicReference<Prompt> lastPrompt = new AtomicReference<>();

        @Override
        public ChatResponse call(Prompt prompt) {
            lastPrompt.set(prompt);
            return response();
        }

        @Override
        public Flux<ChatResponse> stream(Prompt prompt) {
            lastPrompt.set(prompt);
            return Flux.just(response());
        }

        private static ChatResponse response() {
            return new ChatResponse(List.of(new Generation(new AssistantMessage("好的"))));
        }
    }

    @AfterEach
    void cleanup() {
        if (skillName != null) {
            skillStore.delete(skillName);
        }
    }

    @Test
    void slashCommandExpandsIntoModelPromptButKeepsRawContentInDb() throws Exception {
        var admin = userRepository.findByUsername("admin").orElseThrow();
        skillName = "e2e-" + Long.toHexString(System.nanoTime());
        skillStore.write(SkillFile.of(skillName, "端到端测试", null,
                "把以下内容翻译成英文：$ARGUMENTS", true, Instant.now()));

        CapturingChatModel model = new CapturingChatModel();
        when(chatClientFactory.getDefault()).thenReturn(ChatClient.builder(model).build());

        var auth = new UsernamePasswordAuthenticationToken(
                new AuthPrincipal(admin.getId(), admin.getUsername(), admin.getRole()),
                null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        String rawContent = "/" + skillName + " 你好世界";

        MvcResult started = mvc.perform(post("/api/v1/chat/stream")
                        .with(authentication(auth))
                        .contentType(APPLICATION_JSON)
                        .content("{\"content\":\"%s\"}".formatted(rawContent)))
                .andExpect(request().asyncStarted())
                .andReturn();
        String body = mvc.perform(asyncDispatch(started))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // 模型轨：收到的是展开后的 skill 指令
        String promptText = model.lastPrompt.get().getContents();
        assertThat(promptText)
                .contains("<skill_instructions name=\"" + skillName + "\"")
                .contains("把以下内容翻译成英文：你好世界")
                .doesNotContain("$ARGUMENTS");

        // 业务轨：落库的用户消息保持命令原文
        String conversationId = body.substring(body.indexOf("conversationId\":\"") + 17,
                body.indexOf("conversationId\":\"") + 17 + 36);
        var messages = messageRepository.findByConversationIdOrderByCreatedAtAsc(
                UUID.fromString(conversationId));
        assertThat(messages.get(0).getContent()).isEqualTo(rawContent);
    }
}
