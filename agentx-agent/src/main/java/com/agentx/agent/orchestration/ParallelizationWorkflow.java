package com.agentx.agent.orchestration;

import com.agentx.agent.domain.WorkflowType;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Parallelization（并行分片）：同一输入从多个独立视角并行分析后聚合（sectioning 变体）。
 * 虚拟线程执行，天然适配 IO 密集的模型调用（官方模式 3）。
 */
@Component
public class ParallelizationWorkflow implements Workflow {

    private static final List<String> PERSPECTIVES = List.of(
            "从技术可行性视角分析下面的方案，列出关键风险：",
            "从成本与投入产出视角分析下面的方案：",
            "从用户价值与体验视角分析下面的方案：");

    @Override
    public WorkflowType type() {
        return WorkflowType.PARALLELIZATION;
    }

    @Override
    public String run(ChatClient client, String input) {
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<CompletableFuture<String>> futures = PERSPECTIVES.stream()
                    .map(p -> CompletableFuture.supplyAsync(
                            () -> client.prompt().user(p + "\n" + input).call().content(), executor))
                    .toList();
            String merged = String.join("\n\n---\n\n",
                    futures.stream().map(CompletableFuture::join).toList());
            return client.prompt()
                    .user("综合以下三个视角的分析，产出一份平衡的结论与建议：\n" + merged)
                    .call()
                    .content();
        }
    }
}
