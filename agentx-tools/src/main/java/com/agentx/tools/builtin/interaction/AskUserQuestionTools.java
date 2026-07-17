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
import java.util.concurrent.TimeUnit;

/**
 * 向用户提问工具（对标 Claude Code AskUserQuestion）：模型在需要用户做选择时调用，
 * 前端渲染选项卡片，用户点选提交后工具返回答案。执行机制同审批网关——
 * 发 question-request 帧 → 阻塞等回传 → 权威终态帧收尾。
 */
@Slf4j
@AgentTool(group = "interaction")
@RequiredArgsConstructor
public class AskUserQuestionTools {

    /** 等待用户作答上限：10 分钟（同审批），超时视同未作答。 */
    private static final long TIMEOUT_MILLIS = 10 * 60 * 1000L;
    /** ChatStreamService 放入 toolContext 的会话上下文键。 */
    public static final String CONTEXT_KEY = "chatStreamContext";

    private final QuestionRegistry registry;
    private final ObjectMapper objectMapper;

    public record QuestionOption(
            @ToolParam(description = "选项标题：简短明确（1-8 个词）") String label,
            @ToolParam(required = false, description = "选项说明：该选择意味着什么、有何取舍") String description) {}

    public record Question(
            @ToolParam(description = "完整的问题文本，以问号结尾") String question,
            @ToolParam(required = false, description = "问题短标签（如「认证方式」「技术选型」，≤12 字）") String header,
            @ToolParam(description = "候选项 2-4 个，互斥且具体；无需提供「其他」——前端自带自由输入") List<QuestionOption> options,
            @ToolParam(required = false, description = "true 允许多选；默认单选") Boolean multiSelect) {}

    @Tool(name = "askUserQuestion", description = """
            当你需要用户在若干方案/偏好间做决定才能继续时，用本工具向用户提问，把选择显式呈现为可点选的选项。
            【何时使用】需求有歧义需要澄清、存在多个合理技术方案需用户拍板、涉及用户偏好（风格/范围/优先级）时。
            【何时禁用】问题可以从上下文或惯例合理推断时不要打断用户；一次交互最多 4 个问题，不要连续多轮调用轰炸。
            【行为】调用后会阻塞等待用户在界面上作答；返回值为用户的选择（JSON）。用户可能跳过某些问题或超时未答——
            此时按你的最佳判断继续。""")
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

        String answersJson;
        try {
            answersJson = future.get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            answersJson = null;
        }
        registry.forget(conversationId, questionId);
        if (answersJson == null) {
            // 权威终态帧：超时/会话结束也要翻转前端卡片，避免停在可点状态
            context.toolEventSink().onQuestionResult(questionId.toString(), "expired", null);
            return "{\"status\":\"expired\",\"note\":\"用户未作答（超时或会话结束），请按你的最佳判断继续\"}";
        }
        context.toolEventSink().onQuestionResult(questionId.toString(), "answered", answersJson);
        return "{\"status\":\"answered\",\"answers\":" + answersJson + "}";
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
                return om;
            }).toList());
            m.put("multiSelect", Boolean.TRUE.equals(q.multiSelect()));
            return m;
        }).toList();
    }
}
