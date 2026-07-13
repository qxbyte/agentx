package com.agentx.coding.web;

import com.agentx.common.api.ApiResponse;
import com.agentx.common.api.ErrorCode;
import com.agentx.common.exception.BizException;
import com.agentx.infra.ai.stream.ApprovalRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import java.util.UUID;

/**
 * 审批回传端点（设计文档 §6）：前端点批准/拒绝后调用，解冻阻塞的工具线程。
 * 独立于 SSE 流的普通 HTTP 请求——这是单向 SSE 里实现双向确认的关键。
 */
@RestController
@RequiredArgsConstructor
public class ApprovalController {

    private final ApprovalRegistry approvalRegistry;

    public record ApprovalDecision(boolean approved) {}

    @PostMapping("/api/v1/chat/approvals/{approvalId}")
    public ApiResponse<Void> resolve(@PathVariable UUID approvalId,
                                     @RequestBody ApprovalDecision decision) {
        boolean hit = approvalRegistry.resolve(approvalId, decision.approved());
        if (!hit) {
            throw new BizException(ErrorCode.NOT_FOUND, "审批项不存在或已处理");
        }
        return ApiResponse.ok();
    }
}
