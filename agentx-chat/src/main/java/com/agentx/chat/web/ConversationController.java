package com.agentx.chat.web;

import com.agentx.auth.security.AuthPrincipal;
import com.agentx.auth.security.CurrentUser;
import com.agentx.chat.service.ConversationService;
import com.agentx.chat.web.dto.ChatDtos.ConversationView;
import com.agentx.chat.web.dto.ChatDtos.CreateConversationRequest;
import com.agentx.chat.web.dto.ChatDtos.MessageView;
import com.agentx.chat.web.dto.ChatDtos.RenameRequest;
import com.agentx.common.api.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/chat/conversations")
@RequiredArgsConstructor
public class ConversationController {
    private final ConversationService conversationService;
    private final com.agentx.chat.service.memory.ModelMemoryService modelMemoryService;

    @GetMapping
    public ApiResponse<List<ConversationView>> list(@CurrentUser AuthPrincipal user) {
        return ApiResponse.ok(conversationService.list(user.id()).stream()
                .map(ConversationView::of).toList());
    }

    @PostMapping
    public ApiResponse<ConversationView> create(@CurrentUser AuthPrincipal user,
                                                @RequestBody(required = false) CreateConversationRequest req) {
        UUID modelConfigId = req == null ? null : req.modelConfigId();
        UUID agentId = req == null ? null : req.agentId();
        java.util.List<UUID> kbIds = req == null ? null : req.kbIds();
        return ApiResponse.ok(ConversationView.of(
                conversationService.create(user.id(), modelConfigId, agentId, kbIds, null)));
    }

    @PatchMapping("/{id}")
    public ApiResponse<ConversationView> rename(@CurrentUser AuthPrincipal user,
                                                @PathVariable UUID id,
                                                @Valid @RequestBody RenameRequest req) {
        return ApiResponse.ok(ConversationView.of(
                conversationService.rename(id, user.id(), req.title())));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@CurrentUser AuthPrincipal user, @PathVariable UUID id) {
        conversationService.delete(id, user.id());
        return ApiResponse.ok();
    }

    @GetMapping("/{id}/messages")
    public ApiResponse<List<MessageView>> messages(@CurrentUser AuthPrincipal user,
                                                   @PathVariable UUID id) {
        return ApiResponse.ok(conversationService.messages(id, user.id()).stream()
                .map(MessageView::of).toList());
    }

    /** 上下文用量（约数）：输入框余量指示环轮询数据源。 */
    @GetMapping("/{id}/context")
    public ApiResponse<com.agentx.chat.service.memory.ModelMemoryService.ContextUsage> context(
            @CurrentUser AuthPrincipal user, @PathVariable UUID id) {
        conversationService.getOwned(id, user.id());
        return ApiResponse.ok(modelMemoryService.usage(id));
    }

    public record CompactResult(int compactedMessages, int summaryChars,
                                int tokensBefore, int tokensAfter) {}

    /** 手动压缩（/compact）：把早期对话压成摘要，释放模型上下文窗口。 */
    @PostMapping("/{id}/compact")
    public ApiResponse<CompactResult> compact(@CurrentUser AuthPrincipal user,
                                              @PathVariable UUID id) {
        conversationService.getOwned(id, user.id());
        var result = modelMemoryService.compact(id, conversationService.latestPlanState(id));
        return ApiResponse.ok(new CompactResult(result.compactedMessages(),
                result.summaryChars(), result.tokensBefore(), result.tokensAfter()));
    }
}
