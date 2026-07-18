package com.agentx.tools.builtin.planning;

import com.agentx.tools.registry.AgentTool;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import java.util.List;
import java.util.Set;

/**
 * 任务清单工具（移植 Claude Code TodoWrite，工具名保持 updatePlan 以兼容既有链路）：
 * 模型把确定要执行的任务拆成 content/activeForm 双形态清单，边执行边翻状态。
 * 工具本体只做校验——清单数据经由 SSE tool-call 帧的 args 直达前端渲染，
 * 并由 ChatStreamService 在 onToolCall 时回写会话 plan_state 持久化。
 * 触发时机与管理规则写在 @Tool description 里（system prompt 是覆盖语义，不能追加）。
 */
@AgentTool(group = "planning")
public class PlanTools {

    private static final Set<String> STATUSES = Set.of("pending", "in_progress", "completed");

    public record TodoItem(
            @ToolParam(description = "任务内容（祈使形，描述要做什么）：如「运行测试」「重构登录模块」")
            String content,
            @ToolParam(description = "进行时形态（该任务执行中对用户展示）：如「正在运行测试」「正在重构登录模块」")
            String activeForm,
            @ToolParam(description = "状态：pending（未开始）/ in_progress（进行中）/ completed（已完成）")
            String status) {}

    @Tool(description = """
            创建并管理当前会话的结构化任务清单，前端固定展示在输入框上方并实时更新——\
            帮助你跟踪进度、组织复杂任务，也让用户随时看到任务进展。

            【何时使用——主动地用】
            1. 复杂多步任务：需要 3 个及以上不同步骤/操作才能完成；
            2. 需要谨慎规划或多项操作的非平凡任务；
            3. 用户明确要求使用任务清单；
            4. 用户一次给出多件要做的事（编号或逗号分隔的列表）；
            5. 开始执行某项任务之前，先把它置为 in_progress（同一时刻只允许一个进行中）；
            6. 完成某项后立即置 completed，并把执行中新发现的后续任务补进清单。

            【何时不用】只有一件直白的事、任务琐碎到记录毫无组织收益、不足 3 个实质步骤、\
            纯对话或信息型回复——这些情况直接做即可。另外（本产品约定）：执行路径尚未确定时\
            不要提前建清单——尤其禁止把 skill 文档的工作流程或含用户决策分支的方案照抄成\
            清单（用户可能选不同岔路，清单无法反映真实进度）；先完成提问/审批等交互，\
            路径确定后再为「确定要执行的那部分」建清单。

            【任务的双形态】每条任务必须同时提供两种描述：\
            content 用祈使形说明要做什么（如「修复认证 bug」）；\
            activeForm 用进行时形态供执行中展示（如「正在修复认证 bug」）。

            【状态与管理】
            - 三种状态：pending（未开始）/ in_progress（进行中，严格恰好一个）/ completed（已完成）；
            - 边执行边实时更新；完成一项立即标记，不要攒到最后一起标；
            - 先完成当前任务再开始新的；不再相关的任务从清单整条移除；
            - 每次调用传全量清单（覆盖式更新，不是增量）。

            【完成的门槛】只有完全做成才能置 completed：测试失败、实现不完整、存在未解决\
            报错、找不到必需文件或依赖时，一律保持 in_progress，并新建一条任务描述需要\
            解决的阻塞项。

            拿不准要不要用时，倾向于用：主动管理任务清单体现严谨，也确保用户的所有要求\
            都被完成。""")
    public String updatePlan(
            @ToolParam(description = "全量任务清单（覆盖式更新，不是增量）") List<TodoItem> todos) {
        if (todos == null || todos.isEmpty()) {
            return "任务清单不能为空：请传入完整的任务列表。";
        }
        for (TodoItem t : todos) {
            if (t.content() == null || t.content().isBlank()) {
                return "存在 content 为空的任务：每条任务必须有祈使形描述。";
            }
            if (t.activeForm() == null || t.activeForm().isBlank()) {
                return "存在 activeForm 为空的任务：每条任务必须有进行时形态描述。";
            }
            if (t.status() == null || !STATUSES.contains(t.status())) {
                return "存在非法状态值：status 只能是 pending / in_progress / completed。";
            }
        }
        long inProgress = todos.stream().filter(t -> "in_progress".equals(t.status())).count();
        boolean allDone = todos.stream().allMatch(t -> "completed".equals(t.status()));
        if (!allDone && inProgress != 1) {
            return "清单已记录，但请保持恰好一个任务为 in_progress（当前 " + inProgress + " 个），请修正后重新调用。";
        }
        if (allDone) {
            return "任务清单已全部完成。";
        }
        return "清单已更新。请继续使用任务清单跟踪进度，推进当前 in_progress 任务。";
    }
}
