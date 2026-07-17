package com.agentx.coding.web;

import com.agentx.auth.security.AuthPrincipal;
import com.agentx.auth.security.CurrentUser;
import com.agentx.coding.runtime.CodingMode;
import com.agentx.coding.runtime.CodingModeRegistry;
import com.agentx.common.api.ApiResponse;
import com.agentx.common.api.ErrorCode;
import com.agentx.common.exception.BizException;
import com.agentx.infra.ai.stream.ApprovalRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import java.util.UUID;

/**
 * 编码模式切换回传端点：模式选择「立即生效」的通道。
 * 每轮开始的模式随流式请求下发；本端点覆盖轮内切换——更新实时模式表让
 * ApprovalGate 对后续工具调用按新模式放行，切到 AUTO 时并把未决审批一次性批准
 * （阻塞中的工具线程立刻解冻执行，前端审批卡经 approval-result 帧翻转为已批准）。
 * 幂等：无在跑轮次时仅更新/忽略实时表，下一轮以请求模式为准。
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class CodingModeController {

    private final CodingModeRegistry modeRegistry;
    private final ApprovalRegistry approvalRegistry;

    public record ModeUpdate(String mode) {}

    @PutMapping("/api/v1/chat/conversations/{conversationId}/coding-mode")
    public ApiResponse<Void> update(@CurrentUser AuthPrincipal user,
                                    @PathVariable UUID conversationId,
                                    @RequestBody ModeUpdate body) {
        CodingMode mode = parse(body.mode());
        boolean hit = modeRegistry.update(conversationId, user.id(), mode);
        if (mode == CodingMode.AUTO) {
            int approved = approvalRegistry.approveConversation(conversationId, user.id());
            if (approved > 0) {
                log.info("模式切换 AUTO 即时生效：会话 {} 自动批准 {} 条未决审批", conversationId, approved);
            }
        }
        log.debug("编码模式切换: conversation={} mode={} liveHit={}", conversationId, mode, hit);
        return ApiResponse.ok();
    }

    private static CodingMode parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "mode 不能为空");
        }
        try {
            return CodingMode.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BizException(ErrorCode.BAD_REQUEST, "非法模式：" + raw);
        }
    }
}
