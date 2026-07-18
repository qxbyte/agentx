package com.agentx.coding.web;

import com.agentx.coding.runtime.LocalToolsSettings;
import com.agentx.common.api.ApiResponse;
import com.agentx.common.api.ErrorCode;
import com.agentx.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * 本机目录浏览（项目目录选择器用）：AgentX 是本地 app,浏览器拿不到原生目录
 * 选择器的绝对路径,由后端提供受限目录列举——范围锁定在本地工具沙箱根
 * (默认家目录)内,只列目录不列文件,隐藏目录默认排除。
 */
@RestController
@RequiredArgsConstructor
public class DirectoryBrowseController {

    private static final int MAX_ENTRIES = 300;

    private final LocalToolsSettings settings;

    public record DirEntry(String name, String path) {}

    public record DirListing(String path, String parent, List<DirEntry> dirs) {}

    /**
     * 调起系统原生目录选择器（macOS Finder）:本地 app 后端与用户同机,
     * osascript 弹出 choose folder,返回用户选择的绝对路径;取消返回 cancelled。
     */
    @org.springframework.web.bind.annotation.PostMapping("/api/v1/coding/fs/pick-native")
    public ApiResponse<java.util.Map<String, Object>> pickNative() {
        try {
            Process process = new ProcessBuilder("osascript",
                    "-e", "tell application \"System Events\" to activate",
                    "-e", "POSIX path of (choose folder with prompt \"\u4e3a AgentX \u9009\u62e9\u9879\u76ee\u76ee\u5f55\")")
                    .redirectErrorStream(false)
                    .start();
            String out = new String(process.getInputStream().readAllBytes()).strip();
            if (!process.waitFor(180, java.util.concurrent.TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return ApiResponse.ok(java.util.Map.of("cancelled", true));
            }
            if (process.exitValue() != 0 || out.isEmpty()) {
                // exit 1 = 用户点了取消(osascript -128)
                return ApiResponse.ok(java.util.Map.of("cancelled", true));
            }
            String path = out.endsWith("/") && out.length() > 1 ? out.substring(0, out.length() - 1) : out;
            return ApiResponse.ok(java.util.Map.of("cancelled", false, "path", path));
        } catch (IOException e) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "调起系统目录选择器失败: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BizException(ErrorCode.INTERNAL_ERROR, "目录选择被中断");
        }
    }

    /** 列出某目录的子目录;path 缺省为沙箱根(家目录)。 */
    @GetMapping("/api/v1/coding/fs/dirs")
    public ApiResponse<DirListing> list(@RequestParam(required = false) String path) {
        Path root;
        try {
            root = Path.of(settings.getRoot()).toRealPath();
        } catch (IOException e) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "本地根目录不可用: " + settings.getRoot());
        }
        Path target = path == null || path.isBlank() ? root : Path.of(path).normalize();
        if (!target.isAbsolute()) {
            target = root.resolve(target).normalize();
        }
        try {
            target = target.toRealPath();
        } catch (IOException e) {
            throw new BizException(ErrorCode.NOT_FOUND, "目录不存在: " + path);
        }
        if (!target.startsWith(root)) {
            throw new BizException(ErrorCode.FORBIDDEN, "目录超出可浏览范围");
        }
        if (!Files.isDirectory(target)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "不是目录: " + path);
        }
        List<DirEntry> dirs;
        try (Stream<Path> entries = Files.list(target)) {
            dirs = entries
                    .filter(Files::isDirectory)
                    .filter(p -> !p.getFileName().toString().startsWith("."))
                    .sorted()
                    .limit(MAX_ENTRIES)
                    .map(p -> new DirEntry(p.getFileName().toString(), p.toString()))
                    .toList();
        } catch (IOException e) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "目录读取失败: " + e.getMessage());
        }
        String parent = target.equals(root) ? null : target.getParent().toString();
        return ApiResponse.ok(new DirListing(target.toString(), parent, dirs));
    }
}
