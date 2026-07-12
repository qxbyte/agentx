package com.agentx.agent.runtime;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.annotation.Tool;
import reactor.core.publisher.Flux;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.assertThat;

/** 最小探针：验证手工构建的 ChatClient 在 2.0 下能完成流式工具循环。 */
class ToolLoopProbeTest {

    static class EchoTools {
        final AtomicInteger invoked = new AtomicInteger();

        @Tool(description = "回显输入")
        public String echo(String text) {
            invoked.incrementAndGet();
            return "echo:" + text;
        }
    }

    static class StubModel implements ChatModel {
        final AtomicInteger round = new AtomicInteger();
        volatile String seenOptions = "";

        private void capture(Prompt prompt) {
            ChatOptions o = prompt.getOptions();
            String tools = o instanceof ToolCallingChatOptions t
                    ? String.valueOf(t.getToolCallbacks().size()) : "n/a";
            seenOptions = (o == null ? "null" : o.getClass().getName()) + " tools=" + tools;
            System.out.println("[probe] options=" + seenOptions);
        }

        /** 2.0 请求装配以 getOptions().mutate() 为基底——必须返回 ToolCallingChatOptions。 */
        @Override
        public ChatOptions getOptions() {
            return ToolCallingChatOptions.builder().build();
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            capture(prompt);
            return respond();
        }

        @Override
        public Flux<ChatResponse> stream(Prompt prompt) {
            capture(prompt);
            return Flux.just(respond());
        }

        ChatResponse respond() {
            if (round.getAndIncrement() == 0) {
                return new ChatResponse(List.of(new Generation(AssistantMessage.builder()
                        .content("")
                        .toolCalls(List.of(new AssistantMessage.ToolCall(
                                "c1", "function", "echo", "{\"text\":\"hi\"}")))
                        .build())));
            }
            return new ChatResponse(List.of(new Generation(new AssistantMessage("完成"))));
        }
    }

    private static ChatClient clientOf(ChatModel model) {
        // 与 ChatClientFactory 相同的构建方式；工具承载由模型 getOptions() 决定
        return ChatClient.builder(model).build();
    }

    @Test
    void blockingToolLoopExecutes() {
        EchoTools tools = new EchoTools();
        StubModel model = new StubModel();
        String result = clientOf(model)
                .prompt()
                .user("test")
                .toolCallbacks(List.of(ToolCallbacks.from(tools)))
                .call()
                .content();
        assertThat(tools.invoked.get()).isEqualTo(1);
        assertThat(result).contains("完成");
    }

    @Test
    void streamingToolLoopExecutes() {
        EchoTools tools = new EchoTools();
        StubModel model = new StubModel();
        String result = String.join("", clientOf(model)
                .prompt()
                .user("test")
                .toolCallbacks(List.of(ToolCallbacks.from(tools)))
                .stream()
                .content()
                .collectList()
                .block());
        assertThat(tools.invoked.get()).isEqualTo(1);
        assertThat(result).contains("完成");
        assertThat(model.round.get()).isEqualTo(2);
    }
}
