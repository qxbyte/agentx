package com.agentx.rag.tools;

import com.agentx.rag.service.HitTestService;
import com.agentx.rag.web.dto.RagDtos.HitTestRequest;
import com.agentx.rag.web.dto.RagDtos.HitView;
import com.agentx.tools.registry.AgentTool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import java.util.List;
import java.util.UUID;

/**
 * 内置示例：知识库语义检索工具（设计文档 §4.5 「跨模块调 RAG 检索示例」）。
 * <p>
 * 演示工具如何在 Agent loop 中主动查知识库——区别于 {@code RagStreamCustomizer}
 * 的自动召回，本工具由模型按需显式调用（如「先查规范再改代码」）。userId 经
 * {@link ToolContext} 取（禁止访问 SecurityContext——Agent 异步执行无请求线程），
 * 归属校验复用 {@link HitTestService}，模型只能检索请求用户自己的知识库。
 */
@Slf4j
@AgentTool(group = "builtin")
@RequiredArgsConstructor
public class KnowledgeSearchTools {

    private static final String CTX_USER_ID = "userId";

    private final HitTestService hitTestService;

    @Tool(description = "在指定知识库中按语义检索最相关的文本片段，用于获取回答问题或处理任务所需的背景知识与规范")
    public String searchKnowledge(
            @ToolParam(description = "检索问题或关键词") String query,
            @ToolParam(description = "知识库 ID（UUID）") String kbId,
            ToolContext toolContext) {
        Object uid = toolContext == null ? null : toolContext.getContext().get(CTX_USER_ID);
        if (uid == null) {
            return "无法确定当前用户，拒绝检索。";
        }
        List<HitView> hits;
        try {
            hits = hitTestService.hitTest(UUID.fromString(kbId), UUID.fromString(uid.toString()),
                    new HitTestRequest(query, null, null));
        } catch (IllegalArgumentException e) {
            return "知识库 ID 非法：" + kbId;
        } catch (Exception e) {
            log.warn("知识库检索工具失败 kb={}: {}", kbId, e.getMessage());
            return "知识库检索失败：" + e.getMessage();
        }
        if (hits.isEmpty()) {
            return "未在该知识库检索到相关内容。";
        }
        StringBuilder sb = new StringBuilder();
        for (HitView h : hits) {
            double score = h.score() == null ? 0 : h.score();
            sb.append("【").append(h.docName()).append("】(相关度 ")
                    .append(String.format("%.0f%%", score * 100)).append(")\n")
                    .append(h.content()).append("\n\n");
        }
        return sb.toString().strip();
    }
}
