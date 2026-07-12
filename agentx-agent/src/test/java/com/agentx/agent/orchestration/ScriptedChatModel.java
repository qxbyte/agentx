package com.agentx.agent.orchestration;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** 测试夹具：按脚本顺序返回应答的 ChatModel，线程安全。 */
class ScriptedChatModel implements ChatModel {

    private final List<String> script;
    private final AtomicInteger cursor = new AtomicInteger();

    ScriptedChatModel(String... responses) {
        this.script = List.of(responses);
    }

    int calls() {
        return cursor.get();
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        int i = Math.min(cursor.getAndIncrement(), script.size() - 1);
        return new ChatResponse(List.of(new Generation(new AssistantMessage(script.get(i)))));
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        return Flux.just(call(prompt));
    }
}
