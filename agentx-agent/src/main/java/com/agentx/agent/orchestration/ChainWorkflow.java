package com.agentx.agent.orchestration;

import com.agentx.agent.domain.WorkflowType;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

/**
 * Chain（提示链）：把任务拆成固定顺序的加工步骤，每步的输出作为下一步输入。
 * 适合可以静态分解的线性任务（官方模式 1）。示例链：提取要点 → 结构化 → 润色。
 */
@Component
public class ChainWorkflow implements Workflow {

    private static final String[] STEPS = {
            "从下面的内容中提取核心要点，每行一条：\n{input}",
            "把下面的要点整理为带小标题的结构化大纲：\n{input}",
            "把下面的大纲改写为一段面向业务读者的流畅中文说明，保留全部信息：\n{input}"
    };

    @Override
    public WorkflowType type() {
        return WorkflowType.CHAIN;
    }

    @Override
    public String run(ChatClient client, String input) {
        String current = input;
        for (String step : STEPS) {
            current = client.prompt()
                    .user(step.replace("{input}", current))
                    .call()
                    .content();
        }
        return current;
    }
}
