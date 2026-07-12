package com.agentx.agent;

import com.agentx.agent.domain.AgentDefinitionRepository;
import com.agentx.auth.domain.SysUserRepository;
import com.agentx.auth.security.AuthPrincipal;
import com.agentx.infra.ai.client.ChatClientFactory;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.client.ChatClient;
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
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ReAct 全链路：会话绑定种子 Agent「生活助手」→ stub 模型首轮发起工具调用
 * （currentWeather）→ ToolCallingAdvisor 执行 → 二轮给出最终回答。
 * 断言 SSE 流包含 tool-call / tool-result / text-delta / done 帧。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AgentReactFlowTest {

    @Autowired MockMvc mvc;
    @Autowired SysUserRepository userRepository;
    @Autowired AgentDefinitionRepository agentRepository;
    @MockitoBean ChatClientFactory chatClientFactory;

    /** 首轮返回工具调用，二轮返回文本回答。 */
    static class ToolCallingStubModel implements ChatModel {
        private final AtomicInteger round = new AtomicInteger();

        /** 2.0 请求装配以 getOptions().mutate() 为基底——必须返回 ToolCallingChatOptions。 */
        @Override
        public org.springframework.ai.chat.prompt.ChatOptions getOptions() {
            return org.springframework.ai.model.tool.ToolCallingChatOptions.builder().build();
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            return respond();
        }

        @Override
        public Flux<ChatResponse> stream(Prompt prompt) {
            return Flux.just(respond());
        }

        private ChatResponse respond() {
            if (round.getAndIncrement() == 0) {
                AssistantMessage withTool = AssistantMessage.builder()
                        .content("")
                        .toolCalls(List.of(new AssistantMessage.ToolCall(
                                "call-1", "function", "currentWeather", "{\"city\":\"北京\"}")))
                        .build();
                return new ChatResponse(List.of(new Generation(withTool)));
            }
            return new ChatResponse(List.of(new Generation(
                    new AssistantMessage("北京今天晴，26 度，适合出门。"))));
        }
    }

    @Test
    void agentConversationExecutesToolAndStreamsEvents() throws Exception {
        org.mockito.Mockito.when(chatClientFactory.getDefault())
                .thenReturn(ChatClient.builder(new ToolCallingStubModel()).build());

        var admin = userRepository.findByUsername("admin").orElseThrow();
        var agent = agentRepository.findByName("生活助手").orElseThrow();
        var auth = new UsernamePasswordAuthenticationToken(
                new AuthPrincipal(admin.getId(), admin.getUsername(), admin.getRole()),
                null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

        // 建绑定 Agent 的会话
        String convBody = mvc.perform(post("/api/v1/chat/conversations")
                        .with(authentication(auth))
                        .contentType(APPLICATION_JSON)
                        .content("{\"agentId\":\"" + agent.getId() + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String conversationId = convBody.substring(convBody.indexOf("\"id\":\"") + 6,
                convBody.indexOf("\"id\":\"") + 6 + 36);

        MvcResult started = mvc.perform(post("/api/v1/chat/stream")
                        .with(authentication(auth))
                        .contentType(APPLICATION_JSON)
                        .content("{\"conversationId\":\"" + conversationId
                                + "\",\"content\":\"北京天气怎么样\"}"))
                .andExpect(request().asyncStarted())
                .andReturn();

        String body = mvc.perform(asyncDispatch(started))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body)
                .contains("\"type\":\"tool-call\"")
                .contains("currentWeather")
                .contains("\"type\":\"tool-result\"")
                .contains("\"type\":\"text-delta\"")
                .contains("\"type\":\"done\"");
    }
}
