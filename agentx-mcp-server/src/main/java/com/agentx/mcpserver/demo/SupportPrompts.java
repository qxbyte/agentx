package com.agentx.mcpserver.demo;

import org.springframework.ai.mcp.annotation.McpArg;
import org.springframework.ai.mcp.annotation.McpPrompt;
import org.springframework.stereotype.Service;

/** @McpPrompt 示例：可复用的提示词模板，客户端可发现并填参调用。 */
@Service
public class SupportPrompts {

    @McpPrompt(name = "ticket-reply", description = "生成客服工单回复的提示词模板")
    public String ticketReply(
            @McpArg(name = "customerName", description = "客户称呼", required = true) String customerName,
            @McpArg(name = "issue", description = "问题描述", required = true) String issue) {
        return """
                你是专业客服。请针对客户「%s」的以下问题，写一份礼貌、给出明确下一步的回复：
                问题：%s
                要求：先共情，再给解决步骤，最后附联系方式占位符。""".formatted(customerName, issue);
    }
}
