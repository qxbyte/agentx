package com.agentx.chat.web;

import com.agentx.auth.security.AuthPrincipal;
import com.agentx.auth.security.CurrentUser;
import com.agentx.chat.service.ChatStreamService;
import com.agentx.chat.web.dto.ChatDtos.RegenerateRequest;
import com.agentx.chat.web.dto.ChatDtos.StreamRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
public class ChatStreamController {
    private final ChatStreamService chatStreamService;

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@CurrentUser AuthPrincipal user,
                             @Valid @RequestBody StreamRequest req) {
        return chatStreamService.stream(user, req);
    }

    /** 重新生成某条助手消息：删除该轮消息并回滚记忆后，以相同提问重跑流式生成。 */
    @PostMapping(value = "/messages/{id}/regenerate", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter regenerate(@CurrentUser AuthPrincipal user,
                                 @PathVariable UUID id,
                                 @RequestBody(required = false) RegenerateRequest req) {
        RegenerateRequest r = req == null ? new RegenerateRequest(null, null) : req;
        return chatStreamService.regenerate(user, id, r.modelConfigId(), r.mode());
    }
}
