package com.agentx.skill.stream;

import com.agentx.infra.ai.stream.ChatStreamContext;
import com.agentx.infra.ai.stream.ChatStreamCustomizer;
import com.agentx.infra.ai.stream.SseNotifyingToolCallback;
import com.agentx.skill.service.SkillService;
import com.agentx.skill.store.SkillFile;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Skill 模型自动触发（M2,对标 Claude Code 的 Skill tool + 渐进式披露）：
 * 每次会话动态构建 skill 工具——工具 description 携带全部可自动触发技能的
 * L1 元数据目录（name + description）,模型判断任务匹配某技能时调用工具按需
 * 加载其完整指令(L2)。目录放工具描述而非 system prompt:system 是覆盖语义
 * (Agent 会话会覆盖),工具描述则始终随工具定义抵达模型。
 */
@Order(18)
@Component
@RequiredArgsConstructor
public class SkillAutoTriggerCustomizer implements ChatStreamCustomizer {

    /** 单次对话加载技能上限：防循环反复加载。 */
    private static final int MAX_SKILL_LOADS = 8;

    private final SkillService skillService;
    private final ObjectMapper objectMapper;

    @Override
    public void customize(ChatStreamContext context, ChatClient.ChatClientRequestSpec spec) {
        List<SkillFile> invocable = skillService.listModelInvocable();
        if (invocable.isEmpty()) {
            return;
        }
        AtomicInteger counter = new AtomicInteger();
        ToolCallback tool = new SkillLoadTool(skillService, objectMapper, invocable);
        spec.toolCallbacks(List.of(new SseNotifyingToolCallback(
                tool, context.toolEventSink(), counter, MAX_SKILL_LOADS)));
    }
}
