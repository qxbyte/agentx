package com.agentx.agent.orchestration;

import com.agentx.agent.domain.WorkflowType;
import com.agentx.common.api.ErrorCode;
import com.agentx.common.exception.BizException;
import com.agentx.infra.ai.client.ChatClientFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Workflow 统一入口：按类型分发到对应编排器。 */
@Service
public class WorkflowRunner {

    private final Map<WorkflowType, Workflow> workflows;
    private final ChatClientFactory chatClientFactory;

    public WorkflowRunner(List<Workflow> workflowList, ChatClientFactory chatClientFactory) {
        Map<WorkflowType, Workflow> map = new EnumMap<>(WorkflowType.class);
        workflowList.forEach(w -> map.put(w.type(), w));
        this.workflows = map;
        this.chatClientFactory = chatClientFactory;
    }

    public String run(WorkflowType type, String input) {
        return run(type, input, chatClientFactory.getDefault());
    }

    public String run(WorkflowType type, String input, ChatClient client) {
        Workflow workflow = workflows.get(type);
        if (workflow == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "不支持的 workflow 类型: " + type);
        }
        return workflow.run(client, input);
    }
}
