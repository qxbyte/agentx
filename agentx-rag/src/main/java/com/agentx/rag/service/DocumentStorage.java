package com.agentx.rag.service;

import com.agentx.common.api.ErrorCode;
import com.agentx.common.exception.BizException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;

/**
 * 文档物理存储：本地磁盘目录（可配），路径结构 {storage-dir}/{kbId}/{docId}.{ext}。
 * 换对象存储（S3/OSS）时仅替换本类。
 */
@Component
public class DocumentStorage {

    /** 允许的扩展名白名单（防可执行文件上传）。 */
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "pdf", "doc", "docx", "ppt", "pptx", "xls", "xlsx",
            "txt", "md", "markdown", "html", "htm", "csv", "json");

    private final Path root;

    public DocumentStorage(@Value("${agentx.rag.storage-dir}") String storageDir) {
        this.root = Path.of(storageDir);
    }

    public Path store(UUID kbId, UUID docId, MultipartFile file) {
        String ext = extensionOf(file.getOriginalFilename());
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "不支持的文件类型: " + ext);
        }
        try {
            Path dir = root.resolve(kbId.toString());
            Files.createDirectories(dir);
            Path target = dir.resolve(docId + "." + ext);
            file.transferTo(target);
            return target;
        } catch (IOException e) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "文件保存失败: " + e.getMessage());
        }
    }

    public void deleteQuietly(String filePath) {
        try {
            Files.deleteIfExists(Path.of(filePath));
        } catch (IOException ignored) {
            // 物理文件残留不阻塞业务删除
        }
    }

    private String extensionOf(String filename) {
        if (filename == null || !filename.contains(".")) {
            throw new BizException(ErrorCode.BAD_REQUEST, "文件名缺少扩展名");
        }
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }
}
