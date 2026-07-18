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
            维护当前执行任务的进度看板（todo 清单），前端固定展示在输入框上方并实时更新。
            【本质】这是执行进度的实时展示，不是事前规划书：只有当你自己即将连续执行一个\
            较大任务、且执行路径已经确定（前方没有待用户选择的岔路、待批准的方案）时，\
            才按你实际要做的动作把它拆成步骤，然后边执行边翻状态。
            【何时使用】任务需要较长时间、可拆成 3 个及以上明显不同的执行步骤，\
            且接下来将由你不间断地完成这些步骤时。
            【何时禁用】简单问答、单步任务、闲聊、纯解释说明一律不用。执行路径尚未确定时\
            也不要用——尤其禁止把 skill 文档里的工作流程、或含用户决策分支的方案照抄成\
            清单（用户可能选不同岔路，清单将无法反映真实进度）；应先完成提问/审批等交互，\
            路径确定后再为「确定要执行的那部分」建清单。
            【规则】每次调用传全量步骤列表（覆盖式，不是增量）；开始做某步前先置 in_progress，\
            做完立即置 completed，除全部完成外任意时刻恰好一个 in_progress；执行中认知变化\
            可增删调整后续步骤；步骤描述简短、动词开头；title 按任务内容命名（如「重构登录\
            模块」），不要用「计划」「任务」等泛化词。""")
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
