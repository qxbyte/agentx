package com.agentx.tools.builtin.interaction;

import com.agentx.infra.ai.stream.ChatStreamContext;
import com.agentx.infra.ai.stream.QuestionRegistry;
import com.agentx.tools.registry.AgentTool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import tools.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 向用户提问工具（对标 Claude Code AskUserQuestion）：模型在需要用户做选择时调用，
 * 前端渲染选项卡片，用户点选提交后工具返回答案。执行机制同审批网关——
 * 发 question-request 帧 → 阻塞等回传 → 权威终态帧收尾。
 */
@Slf4j
@AgentTool(group = "interaction")
@RequiredArgsConstructor
public class AskUserQuestionTools {

    /** ChatStreamService 放入 toolContext 的会话上下文键。 */
    public static final String CONTEXT_KEY = "chatStreamContext";

    private final QuestionRegistry registry;
    private final ObjectMapper objectMapper;

    public record QuestionOption(
            @ToolParam(description = "选项标题：简短明确（1-5 个词）；推荐项放在首位并在末尾加「（推荐）」") String label,
            @ToolParam(required = false, description = "选项说明：该选择意味着什么、有何取舍") String description,
            @ToolParam(required = false, description = "预览内容（多行文本，等宽渲染）：代码片段、UI 示意、配置样例等"
                    + "需要用户对照比较的具体产物；仅单选问题支持——任一选项带预览时卡片切换为"
                    + "「左选项列表 + 右预览面板」并排布局，预览随选中项切换。"
                    + "普通偏好类问题不要加预览") String preview) {}

    public record Question(
            @ToolParam(description = "完整的问题文本，以问号结尾") String question,
            @ToolParam(required = false, description = "问题短标签（如「认证方式」「技术选型」，≤12 字）") String header,
            @ToolParam(description = "候选项 2-4 个，互斥且具体；无需提供「其他」——前端自带自由输入") List<QuestionOption> options,
            @ToolParam(required = false, description = "true 允许多选（选项可叠加不互斥时用）；默认单选") Boolean multiSelect) {}

    @Tool(name = "askUserQuestion", description = """
            当你需要用户在若干方案/偏好间做决定才能继续时，用本工具向用户提问，把选择显式呈现为可点选的选项。
            【何时使用】需求有歧义需要澄清、存在多个合理技术方案需用户拍板、涉及用户偏好（风格/范围/优先级）时。
            【何时禁用】问题可以从上下文或惯例合理推断时不要打断用户；一次交互最多 4 个问题，不要连续多轮调用轰炸。
            【选择器形态】默认单选；multiSelect=true 为多选（选项可叠加时用）；选项可带 preview
            （代码片段/UI 示意/配置样例等需要视觉对照的具体产物，仅单选支持，前端切换为并排预览布局）——
            普通偏好问题不要加预览。有明确推荐时把推荐项放首位并在 label 末尾加「（推荐）」。
            【行为】调用后会一直等待用户在界面上作答（不设超时）；返回值为用户的选择（JSON）。
            用户可能跳过某些问题，或会话结束仍未作答——此时按你的最佳判断继续。""")
    public String askUserQuestion(
            @ToolParam(description = "问题列表（1-4 个），多个问题将以分步卡片依次呈现") List<Question> questions,
            ToolContext toolContext) {
        if (questions == null || questions.isEmpty() || questions.size() > 4) {
            return "问题数量须为 1-4 个。";
        }
        for (Question q : questions) {
            if (q.question() == null || q.question().isBlank()
                    || q.options() == null || q.options().size() < 2 || q.options().size() > 4) {
                return "每个问题必须有问题文本和 2-4 个候选项。";
            }
        }
        ChatStreamContext context = toolContext == null
                ? null : (ChatStreamContext) toolContext.getContext().get(CONTEXT_KEY);
        if (context == null) {
            return "当前会话不支持交互式提问，请改为在回复中直接列出选项并请用户文字回复。";
        }

        UUID questionId = UUID.randomUUID();
        UUID conversationId = context.conversationId();
        CompletableFuture<String> future =
                registry.register(context.userId(), conversationId, questionId);
        context.toolEventSink().onQuestionRequest(questionId.toString(), toPayload(questions));

        // 无限期等待(对标 Claude Code:提问不设超时):阻塞的是虚拟线程,挂起成本
        // 仅几 KB 堆内存,且等待期间不占用任何模型连接。唯一的终止条件是用户作答
        // 或会话流终止(关页面/停止/服务重启)——断流时 cancelConversation 以 null 收尾
        String answersJson;
        try {
            answersJson = future.get();
        } catch (Exception e) {
            answersJson = null;
        }
        registry.forget(conversationId, questionId);
        if (answersJson == null) {
            // 权威终态帧：会话结束未作答也要翻转前端卡片，避免停在可点状态
            context.toolEventSink().onQuestionResult(questionId.toString(), "expired", null);
            return "{\"status\":\"expired\",\"note\":\"用户未作答（会话已结束），请按你的最佳判断继续\"}";
        }
        context.toolEventSink().onQuestionResult(questionId.toString(), "answered", answersJson);
        // 对标 Claude Code 的 system-reminder 注入:交互是打断模型任务心流的断点,
        // 在结果里附带清单提醒,避免模型作答后忘记推进/更新 todo 清单
        return "{\"status\":\"answered\",\"answers\":" + answersJson
                + ",\"reminder\":\"If you are tracking this task with a todo list, update it now"
                + " (mark finished items completed, set the next task in_progress) and proceed"
                + " with the remaining tasks.\"}";
    }

    /** 问题清单 → SSE 帧的结构化 payload（前端渲染契约）。 */
    private List<Map<String, Object>> toPayload(List<Question> questions) {
        return questions.stream().<Map<String, Object>>map(q -> {
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("question", q.question());
            if (q.header() != null && !q.header().isBlank()) m.put("header", q.header());
            m.put("options", q.options().stream().<Map<String, Object>>map(o -> {
                Map<String, Object> om = new java.util.LinkedHashMap<>();
                om.put("label", o.label());
                if (o.description() != null && !o.description().isBlank()) {
                    om.put("description", o.description());
                }
                if (o.preview() != null && !o.preview().isBlank()
                        && !Boolean.TRUE.equals(q.multiSelect())) {
                    om.put("preview", o.preview());
                }
                return om;
            }).toList());
            m.put("multiSelect", Boolean.TRUE.equals(q.multiSelect()));
            return m;
        }).toList();
    }
}
