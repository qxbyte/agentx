package com.agentx.agent.orchestration;

import com.agentx.agent.domain.WorkflowType;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

/**
 * Evaluator-Optimizer（评估-优化）：生成 → 评审 → 按意见迭代改进，
 * 直至评审通过或达轮次上限（官方模式 5）。适合有明确评价标准的产出。
 */
@Component
public class EvaluatorOptimizerWorkflow implements Workflow {

    private static final int MAX_ROUNDS = 3;
    private static final String PASS_MARK = "PASS";

    @Override
    public WorkflowType type() {
        return WorkflowType.EVALUATOR_OPTIMIZER;
    }

    @Override
    public String run(ChatClient client, String input) {
        String draft = client.prompt().user(input).call().content();
        for (int round = 0; round < MAX_ROUNDS; round++) {
            String review = client.prompt()
                    .user("评审下面这份对任务「" + input + "」的回答。如果完全合格只输出 "
                            + PASS_MARK + "；否则列出具体改进意见：\n" + draft)
                    .call()
                    .content();
            if (review.strip().startsWith(PASS_MARK)) {
                return draft;
            }
            draft = client.prompt()
                    .user("根据评审意见改进回答。\n任务：" + input
                            + "\n当前回答：\n" + draft + "\n评审意见：\n" + review)
                    .call()
                    .content();
        }
        return draft;
    }
}
