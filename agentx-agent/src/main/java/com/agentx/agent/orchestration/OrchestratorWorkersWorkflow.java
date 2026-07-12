package com.agentx.agent.orchestration;

import com.agentx.agent.domain.WorkflowType;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Orchestrator-Workers（编排者-工人）：编排者动态拆解子任务 → 工人并行执行 →
 * 汇总者合成。区别于 Parallelization：子任务由模型按输入动态决定（官方模式 4）。
 */
@Component
public class OrchestratorWorkersWorkflow implements Workflow {

    private static final int MAX_SUBTASKS = 5;

    @Override
    public WorkflowType type() {
        return WorkflowType.ORCHESTRATOR_WORKERS;
    }

    @Override
    public String run(ChatClient client, String input) {
        String plan = client.prompt()
                .user("把下面的任务拆解为最多 " + MAX_SUBTASKS
                        + " 个可独立完成的子任务，每行一个，不要编号和额外说明：\n" + input)
                .call()
                .content();
        List<String> subtasks = Arrays.stream(plan.split("\n"))
                .map(String::strip)
                .filter(s -> !s.isEmpty())
                .limit(MAX_SUBTASKS)
                .toList();

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<CompletableFuture<String>> futures = subtasks.stream()
                    .map(task -> CompletableFuture.supplyAsync(
                            () -> "子任务：" + task + "\n结果：" + client.prompt()
                                    .user("完成下面的子任务（属于总任务「" + input + "」的一部分）：\n" + task)
                                    .call().content(), executor))
                    .toList();
            String workerResults = String.join("\n\n",
                    futures.stream().map(CompletableFuture::join).toList());
            return client.prompt()
                    .user("以下是各子任务的完成结果，请合成为对总任务的完整回答：\n总任务：" + input
                            + "\n\n" + workerResults)
                    .call()
                    .content();
        }
    }
}
