package com.agentx.infra.ai.stream;

import org.springframework.ai.chat.client.ChatClient;

/**
 * 流式对话请求定制 SPI（chat 模块的扩展点）。
 * <p>
 * chat 层组装 prompt 后依序调用所有实现；agent 模块（M3）据 agentId 注入
 * system prompt / 工具集，rag 模块（M4）据知识库绑定注入检索 advisor。
 * chat 不依赖任何上层模块——依赖方向：agent/rag → infra-ai ← chat。
 */
public interface ChatStreamCustomizer {

    /** 定制本次请求；实现方对不相关的会话应快速返回。 */
    void customize(ChatStreamContext context, ChatClient.ChatClientRequestSpec spec);
}
