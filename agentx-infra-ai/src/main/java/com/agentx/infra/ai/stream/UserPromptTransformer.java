package com.agentx.infra.ai.stream;

import java.util.UUID;

/**
 * 用户输入前置变换 SPI（chat 模块的扩展点，与 {@link ChatStreamCustomizer} 同构）。
 * <p>
 * chat 层在附件包装前依序（@Order 排序）调用所有实现，对本轮 user prompt 做文本
 * 变换；skill 模块据此实现 /name 斜杠命令展开。实现方对不匹配的输入必须原样返回。
 * 业务轨（chat_message.content）保留用户原文，变换结果只进模型轨与记忆。
 * 依赖方向：skill → infra-ai ← chat。
 */
public interface UserPromptTransformer {

    /** 变换本轮用户输入；不匹配时原样返回。 */
    String transform(UserPromptContext context, String content);

    /** 轻量上下文：变换发生在流建立早期，{@link ChatStreamContext}（含 SSE sink）尚未组装。 */
    record UserPromptContext(UUID userId, UUID conversationId) {}
}
