package com.agentx.chat.web;

import com.agentx.auth.security.AuthPrincipal;
import com.agentx.auth.security.CurrentUser;
import com.agentx.chat.domain.ChatAttachment;
import com.agentx.chat.domain.ChatAttachmentRepository;
import com.agentx.chat.service.AttachmentService;
import com.agentx.common.api.ApiResponse;
import com.agentx.common.api.ErrorCode;
import com.agentx.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

/** 会话附件上传：multipart 批量，上传即解析，返回逐文件结果（失败项不阻塞其余）。 */
@RestController
@RequestMapping("/api/v1/attachments")
@RequiredArgsConstructor
public class AttachmentController {

    private final AttachmentService attachmentService;
    private final ChatAttachmentRepository repository;

    @PostMapping
    public ApiResponse<List<AttachmentService.UploadResult>> upload(
            @CurrentUser AuthPrincipal user,
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam(value = "relPaths", required = false) List<String> relPaths) {
        return ApiResponse.ok(attachmentService.upload(user.id(), files, relPaths));
    }

    /** 原文件回显（历史消息里的图片缩略图）：按 userId 归属校验，越权 404。 */
    @GetMapping("/{id}/raw")
    public ResponseEntity<byte[]> raw(@CurrentUser AuthPrincipal user, @PathVariable UUID id) {
        ChatAttachment attachment = repository.findByIdAndUserId(id, user.id())
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "附件不存在"));
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(Path.of(attachment.getStoragePath()));
        } catch (IOException e) {
            throw new BizException(ErrorCode.NOT_FOUND, "附件文件已被清理");
        }
        String ext = attachment.getFilename()
                .substring(attachment.getFilename().lastIndexOf('.') + 1).toLowerCase();
        String contentType = switch (ext) {
            case "png" -> "image/png";
            case "webp" -> "image/webp";
            case "gif" -> "image/gif";
            case "jpg", "jpeg" -> "image/jpeg";
            default -> "application/octet-stream";
        };
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePrivate())
                .contentType(MediaType.parseMediaType(contentType))
                .body(bytes);
    }
}
