package com.agentx.tools.builtin.planning;

import com.agentx.tools.registry.AgentTool;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import java.util.List;
import java.util.Set;

/**
 * 任务计划工具（对标 Codex update_plan）：模型把大任务拆成子任务清单并随执行推进状态。
 * 工具本体只做校验——计划数据经由 SSE tool-call 帧的 args 直达前端渲染，
 * 并由 ChatStreamService 在 onToolCall 时回写会话 plan_state 持久化。
 * 触发时机的保守约束写在 @Tool description 里（system prompt 是覆盖语义，不能追加）。
 */
@AgentTool(group = "planning")
public class PlanTools {

    private static final Set<String> STATUSES = Set.of("pending", "in_progress", "completed");

    public record PlanItem(
            @ToolParam(description = "子任务描述：动词开头，一句话以内") String step,
            @ToolParam(description = "状态：pending（待办）/ in_progress（进行中）/ completed（已完成）")
            String status) {}

    @Tool(description = """
            维护当前任务的执行计划（todo 清单），前端会固定展示在输入框上方并实时更新。
            【何时使用】仅当任务确实庞大——需要 3 个及以上明显不同的步骤/阶段才能完成，\
            或用户明确要求列计划时才使用；开始执行前先列出完整计划，之后每完成一个步骤\
            都必须再次调用本工具更新状态，直到全部 completed。
            【何时禁用】简单问答、单步任务、闲聊、纯解释说明类回复，一律不要调用。
            【规则】每次调用传全量步骤列表（覆盖式，不是增量）；除全部完成外，\
            任意时刻保持恰好一个步骤为 in_progress；步骤描述简短、动词开头；\
            title 按任务内容命名（如「重构登录模块」），不要用「计划」「任务」等泛化词。""")
    public String updatePlan(
            @ToolParam(description = "计划标题：根据任务内容起的简短名称（2~10 字），每次调用保持一致") String title,
            @ToolParam(description = "全量计划步骤（覆盖式更新）") List<PlanItem> steps,
            @ToolParam(required = false, description = "本次计划变更的一句话说明") String explanation) {
        if (steps == null || steps.isEmpty()) {
            return "计划不能为空：请传入完整的步骤列表。";
        }
        boolean invalid = steps.stream()
                .anyMatch(s -> s.status() == null || !STATUSES.contains(s.status()));
        if (invalid) {
            return "存在非法状态值：status 只能是 pending / in_progress / completed。";
        }
        long inProgress = steps.stream().filter(s -> "in_progress".equals(s.status())).count();
        boolean allDone = steps.stream().allMatch(s -> "completed".equals(s.status()));
        if (!allDone && inProgress != 1) {
            return "计划已记录，但请保持恰好一个步骤为 in_progress（当前 " + inProgress + " 个），请修正后重新调用。";
        }
        // title 缺失不拒绝（面板回落默认名），但提醒模型下次带上
        if (title == null || title.isBlank()) {
            return (allDone ? "计划全部完成。" : "计划已更新。") + "请在后续调用中补充 title（按任务内容命名的简短标题）。";
        }
        return allDone ? "计划全部完成。" : "计划已更新。";
    }
}
