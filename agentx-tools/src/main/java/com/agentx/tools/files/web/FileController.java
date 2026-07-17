package com.agentx.tools.files.web;

import com.agentx.auth.security.AuthPrincipal;
import com.agentx.auth.security.CurrentUser;
import com.agentx.common.api.ErrorCode;
import com.agentx.common.exception.BizException;
import com.agentx.tools.files.GeneratedFile;
import com.agentx.tools.files.GeneratedFileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

/**
 * 生成文件下载端点。归属校验按 userId（越权表现为 404，不泄露存在性），
 * 前端以 axios blob 方式携带 Bearer 下载。
 */
@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
public class FileController {

    private static final Map<String, String> CONTENT_TYPES = Map.of(
            "md", "text/markdown; charset=UTF-8",
            "docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "pdf", "application/pdf",
            "pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final GeneratedFileRepository repository;

    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> download(@CurrentUser AuthPrincipal user, @PathVariable UUID id) {
        GeneratedFile file = repository.findByIdAndUserId(id, user.id())
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "文件不存在"));
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(Path.of(file.getPath()));
        } catch (IOException e) {
            throw new BizException(ErrorCode.NOT_FOUND, "文件已被清理，无法下载");
        }
        String encoded = URLEncoder.encode(file.getFilename(), StandardCharsets.UTF_8)
                .replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + encoded)
                .contentType(MediaType.parseMediaType(
                        CONTENT_TYPES.getOrDefault(file.getFormat(), "application/octet-stream")))
                .body(bytes);
    }
}
