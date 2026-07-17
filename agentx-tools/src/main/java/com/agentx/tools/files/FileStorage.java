package com.agentx.tools.files;

import com.agentx.common.api.ErrorCode;
import com.agentx.common.exception.BizException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * 生成文件的受管存储：{storage-dir}/{conversationId|misc}/{fileId}.{ext}。
 * 与 RAG DocumentStorage 同构，换对象存储时仅替换本类。
 */
@Component
public class FileStorage {

    private final Path root;

    public FileStorage(@Value("${agentx.files.storage-dir:${user.home}/.agentx/generated}") String dir) {
        this.root = Path.of(dir);
    }

    public Path store(UUID conversationId, UUID fileId, String ext, byte[] bytes) {
        try {
            Path dir = root.resolve(conversationId == null ? "misc" : conversationId.toString());
            Files.createDirectories(dir);
            Path target = dir.resolve(fileId + "." + ext);
            Files.write(target, bytes);
            return target;
        } catch (IOException e) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "生成文件保存失败: " + e.getMessage());
        }
    }
}
