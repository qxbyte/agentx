package com.agentx.coding.runtime;

import com.agentx.infra.ai.stream.ChatStreamContext;
import org.springframework.ai.tool.ToolCallback;

/**
 * 危险工具的审批装饰入口（ASK 模式）。C3 里实现为"发 approval-request 帧 +
 * 虚拟线程阻塞等回传"；在此定义接口以便 {@link CodingStreamCustomizer} 依赖。
 */
public interface CodingApprovalDecorator {

    /** 用审批网关包裹一个危险工具回调。 */
    ToolCallback decorate(ToolCallback delegate, ChatStreamContext context);
}
