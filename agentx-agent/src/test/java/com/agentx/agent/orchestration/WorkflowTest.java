package com.agentx.agent.orchestration;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import static org.assertj.core.api.Assertions.assertThat;

class WorkflowTest {

    private static ChatClient client(ScriptedChatModel model) {
        return ChatClient.builder(model).build();
    }

    @Test
    void chainRunsAllStepsInOrder() {
        ScriptedChatModel model = new ScriptedChatModel("要点", "大纲", "终稿");
        String result = new ChainWorkflow().run(client(model), "原始素材");
        assertThat(result).isEqualTo("终稿");
        assertThat(model.calls()).isEqualTo(3);
    }

    @Test
    void routingDispatchesToClassifiedExpert() {
        ScriptedChatModel model = new ScriptedChatModel("technical", "已按技术专家处理");
        String result = new RoutingWorkflow().run(client(model), "服务 OOM 了怎么排查");
        assertThat(result).isEqualTo("已按技术专家处理");
        assertThat(model.calls()).isEqualTo(2);
    }

    @Test
    void evaluatorReturnsDraftOnPass() {
        ScriptedChatModel model = new ScriptedChatModel("初稿", "PASS");
        String result = new EvaluatorOptimizerWorkflow().run(client(model), "写一段介绍");
        assertThat(result).isEqualTo("初稿");
        assertThat(model.calls()).isEqualTo(2);
    }

    @Test
    void evaluatorIteratesUntilPass() {
        ScriptedChatModel model = new ScriptedChatModel(
                "初稿", "结构混乱，需要分段", "二稿", "PASS");
        String result = new EvaluatorOptimizerWorkflow().run(client(model), "写一段介绍");
        assertThat(result).isEqualTo("二稿");
        assertThat(model.calls()).isEqualTo(4);
    }

    @Test
    void orchestratorFansOutAndSynthesizes() {
        ScriptedChatModel model = new ScriptedChatModel(
                "调研现状\n输出方案", "子结果A", "子结果B", "综合结论");
        String result = new OrchestratorWorkersWorkflow().run(client(model), "做一份技术选型");
        assertThat(result).isEqualTo("综合结论");
        assertThat(model.calls()).isEqualTo(4);
    }

    @Test
    void parallelizationMergesPerspectives() {
        ScriptedChatModel model = new ScriptedChatModel("视角1", "视角2", "视角3", "综合建议");
        String result = new ParallelizationWorkflow().run(client(model), "评估方案X");
        assertThat(result).isEqualTo("综合建议");
        assertThat(model.calls()).isEqualTo(4);
    }
}
