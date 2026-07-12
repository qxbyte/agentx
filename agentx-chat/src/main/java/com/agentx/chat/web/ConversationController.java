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
        return ApiResponse.ok(ConversationView.of(
                conversationService.create(user.id(), modelConfigId, agentId)));
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
}
