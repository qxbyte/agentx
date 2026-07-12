package com.agentx.agent.orchestration;

import com.agentx.agent.domain.WorkflowType;
import org.springframework.ai.chat.client.ChatClient;

/**
 * Workflow 编排器 SPI —— 官方五种 agentic 模式的统一入口（设计文档 §4.6）。
 * 实现为无状态 bean，由 {@link WorkflowRunner} 按类型分发；
 * 复杂业务编排：新增实现类即可，配置化 Agent 通过 workflow_type 引用。
 */
public interface Workflow {

    WorkflowType type();

    /** 同步执行一次编排，返回最终文本结果。 */
    String run(ChatClient client, String input);
}
