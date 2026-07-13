package com.agentx.coding.sandbox;

import com.agentx.common.api.ErrorCode;
import com.agentx.common.exception.BizException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 路径沙箱（设计文档 §4 安全边界）：把用户/模型给的相对路径解析到工作区根内，
 * 并保证解析结果不逃逸出根目录。两道校验：
 *   1) normalize 后的 lexical 前缀检查（挡纯 ../ 逃逸）；
 *   2) 对已存在路径额外 toRealPath 检查（挡符号链接逃逸）。
 * 一个沙箱实例绑定一个工作区根，工具执行期短生命周期使用。
 */
public final class PathSandbox {

    private final Path root;

    private PathSandbox(Path root) {
        this.root = root;
    }

    /** 用工作区根构建；根必须存在且为目录。 */
    public static PathSandbox of(String rootPath) {
        Path root;
        try {
            root = Path.of(rootPath).toRealPath();
        } catch (IOException e) {
            throw new BizException(ErrorCode.NOT_FOUND, "工作区目录不存在: " + rootPath);
        }
        if (!Files.isDirectory(root)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "工作区路径不是目录: " + rootPath);
        }
        return new PathSandbox(root);
    }

    public Path root() {
        return root;
    }

    /**
     * 解析相对路径到工作区内的绝对路径，越界抛异常。
     * 允许目标尚不存在（写文件场景）——此时校验其父目录不越界。
     */
    public Path resolve(String relative) {
        String cleaned = relative == null ? "" : relative.strip();
        // 绝对路径按"相对根"处理：去掉前导分隔符，禁止直接跳到文件系统根
        while (cleaned.startsWith("/")) {
            cleaned = cleaned.substring(1);
        }
        Path candidate = root.resolve(cleaned).normalize();
        if (!candidate.startsWith(root)) {
            throw new BizException(ErrorCode.FORBIDDEN, "路径越界，拒绝访问: " + relative);
        }
        // 已存在则对真实路径再校验（防符号链接逃逸）
        if (Files.exists(candidate)) {
            try {
                Path real = candidate.toRealPath();
                if (!real.startsWith(root)) {
                    throw new BizException(ErrorCode.FORBIDDEN, "路径经符号链接越界: " + relative);
                }
                return real;
            } catch (IOException e) {
                throw new BizException(ErrorCode.INTERNAL_ERROR, "路径解析失败: " + relative);
            }
        }
        return candidate;
    }

    /** 相对工作区根的展示路径（回给模型/前端时用，不泄露服务器绝对路径）。 */
    public String relativize(Path absolute) {
        return root.relativize(absolute).toString();
    }
}
