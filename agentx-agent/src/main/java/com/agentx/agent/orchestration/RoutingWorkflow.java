package com.agentx.agent.orchestration;

import com.agentx.agent.domain.WorkflowType;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;
import java.util.Map;

/**
 * Routing（路由）：先分类，再分发给对应的"专家 prompt"。
 * 适合输入类别差异大、各类需要专门处理策略的场景（官方模式 2）。
 */
@Component
public class RoutingWorkflow implements Workflow {

    private static final Map<String, String> ROUTES = Map.of(
            "technical", "你是资深技术支持工程师，用准确的技术语言解决用户问题：\n",
            "billing", "你是账务专员，谨慎、合规地处理费用相关问题，涉及退款要说明流程：\n",
            "general", "你是友好的客服助手，简洁清晰地回应：\n");

    @Override
    public WorkflowType type() {
        return WorkflowType.ROUTING;
    }

    @Override
    public String run(ChatClient client, String input) {
        String category = client.prompt()
                .user("把下面的用户请求分类，只输出一个词（technical/billing/general）：\n" + input)
                .call()
                .content()
                .strip().toLowerCase();
        String expertPrompt = ROUTES.getOrDefault(category, ROUTES.get("general"));
        return client.prompt().user(expertPrompt + input).call().content();
    }
}
