package com.agentx.chat.web;

import com.agentx.auth.security.AuthPrincipal;
import com.agentx.auth.security.CurrentUser;
import com.agentx.common.api.ApiResponse;
import com.agentx.common.api.ErrorCode;
import com.agentx.common.exception.BizException;
import com.agentx.infra.ai.stream.QuestionRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 提问答案回传端点（askUserQuestion 工具）：前端提交选择后调用，
 * 解冻阻塞的工具线程——机制同审批回传（单向 SSE 里的双向确认）。
 */
@RestController
@RequiredArgsConstructor
public class QuestionController {

    private final QuestionRegistry questionRegistry;
    private final ObjectMapper objectMapper;

    /** 每问一条：selected 为选中项 label 列表;otherText 自由输入;skipped 表示跳过。 */
    public record AnswerSubmission(List<Map<String, Object>> answers) {}

    @PostMapping("/api/v1/chat/questions/{questionId}")
    public ApiResponse<Void> answer(@CurrentUser AuthPrincipal user,
                                    @PathVariable UUID questionId,
                                    @RequestBody AnswerSubmission submission) {
        if (submission.answers() == null || submission.answers().isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "答案不能为空");
        }
        String answersJson = objectMapper.writeValueAsString(submission.answers());
        // 归属校验在 registry 内完成：非本人会话的提问视同不存在
        boolean hit = questionRegistry.resolve(questionId, user.id(), answersJson);
        if (!hit) {
            throw new BizException(ErrorCode.NOT_FOUND, "提问不存在或已处理");
        }
        return ApiResponse.ok();
    }
}
