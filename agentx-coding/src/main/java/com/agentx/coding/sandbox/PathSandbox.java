package com.agentx.coding.sandbox;

import com.agentx.common.api.ErrorCode;
import com.agentx.common.exception.BizException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 路径沙箱（设计文档 §4 安全边界）：把用户/模型给的路径解析到允许范围内，
 * 并保证解析结果不逃逸。两道校验：
 *   1) normalize 后的 lexical 前缀检查（挡纯 ../ 逃逸）；
 *   2) 对已存在路径额外 toRealPath 检查（挡符号链接逃逸）。
 * <p>
 * 支持可选的**只读第二根**（如家目录）：`~/` 前缀与第二根内的绝对路径放行——
 * 编码会话的只读工具据此可越出工作区读本机文件（AgentX 是本地 app）；
 * 写入类工具仍用严格单根沙箱。
 */
public final class PathSandbox {

    private final Path root;
    /** 只读第二根;null 表示严格单根。 */
    private final Path secondary;
    /** BYPASS 模式:不做任何边界校验,root 仅作相对路径基准与 shell cwd。 */
    private final boolean unrestricted;

    private PathSandbox(Path root, Path secondary, boolean unrestricted) {
        this.root = root;
        this.secondary = secondary;
        this.unrestricted = unrestricted;
    }

    /** 严格单根：根必须存在且为目录。 */
    public static PathSandbox of(String rootPath) {
        return of(rootPath, null);
    }

    /** 主根 + 可选只读第二根（第二根不可用时静默退化为严格单根）。 */
    public static PathSandbox of(String rootPath, String secondaryRoot) {
        Path root;
        try {
            root = Path.of(rootPath).toRealPath();
        } catch (IOException e) {
            throw new BizException(ErrorCode.NOT_FOUND, "工作区目录不存在: " + rootPath);
        }
        if (!Files.isDirectory(root)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "工作区路径不是目录: " + rootPath);
        }
        Path secondary = null;
        if (secondaryRoot != null && !secondaryRoot.isBlank()) {
            try {
                Path candidate = Path.of(secondaryRoot).toRealPath();
                if (Files.isDirectory(candidate)) {
                    secondary = candidate;
                }
            } catch (IOException e) {
                // 第二根不可用:退化为严格单根,不阻塞主流程
            }
        }
        return new PathSandbox(root, secondary, false);
    }

    /**
     * BYPASS 模式的无边界沙箱：root 仅作相对路径基准（与 shell cwd），
     * 绝对路径、~/、越界路径一律放行——等同用户本人在终端里操作。
     */
    public static PathSandbox unrestricted(String rootPath) {
        Path root;
        try {
            root = Path.of(rootPath).toRealPath();
        } catch (IOException e) {
            throw new BizException(ErrorCode.NOT_FOUND, "工作区目录不存在: " + rootPath);
        }
        return new PathSandbox(root, null, true);
    }

    public Path root() {
        return root;
    }

    /**
     * 解析路径,越界抛异常。相对路径 → 主根;`~`/`~/x` → 第二根(未配置则按主根相对);
     * 绝对路径落在主根或第二根内直接接受,其余按"相对主根"降级处理。
     * 允许目标尚不存在（写文件场景）——此时校验其父目录不越界。
     */
    public Path resolve(String relative) {
        String cleaned = relative == null ? "" : relative.strip();
        if (unrestricted) {
            return resolveUnrestricted(cleaned);
        }
        Path candidate;
        Path boundary;
        if (cleaned.equals("~") || cleaned.startsWith("~/")) {
            String rest = cleaned.equals("~") ? "" : cleaned.substring(2);
            if (secondary != null) {
                candidate = secondary.resolve(rest).normalize();
                boundary = secondary;
            } else {
                candidate = root.resolve(rest).normalize();
                boundary = root;
            }
        } else {
            Path given = Path.of(cleaned);
            if (given.isAbsolute()) {
                Path normalized = given.normalize();
                Path matched = boundaryOf(normalized);
                if (matched == null && Files.exists(normalized)) {
                    // 词法不匹配但路径存在:按真实路径再试一次
                    // （macOS 的 /var→/private/var 等符号链接前缀,模型常回传非规范形式）
                    try {
                        Path real = normalized.toRealPath();
                        if (boundaryOf(real) != null) {
                            normalized = real;
                            matched = boundaryOf(real);
                        }
                    } catch (IOException ignored) {
                        // 解析失败按未匹配处理
                    }
                }
                if (matched != null) {
                    candidate = normalized;
                    boundary = matched;
                } else {
                    // 范围外的绝对路径按"相对主根"降级:去掉前导分隔符
                    String stripped = cleaned;
                    while (stripped.startsWith("/")) {
                        stripped = stripped.substring(1);
                    }
                    candidate = root.resolve(stripped).normalize();
                    boundary = root;
                }
            } else {
                candidate = root.resolve(cleaned).normalize();
                boundary = root;
            }
        }
        if (!candidate.startsWith(boundary)) {
            throw new BizException(ErrorCode.FORBIDDEN, "路径越界，拒绝访问: " + relative);
        }
        // 已存在则对真实路径再校验（防符号链接逃逸）
        if (Files.exists(candidate)) {
            try {
                Path real = candidate.toRealPath();
                if (!real.startsWith(boundary)) {
                    throw new BizException(ErrorCode.FORBIDDEN, "路径经符号链接越界: " + relative);
                }
                return real;
            } catch (IOException e) {
                throw new BizException(ErrorCode.INTERNAL_ERROR, "路径解析失败: " + relative);
            }
        }
        return candidate;
    }

    /** 无边界解析：~/ → 用户家目录,绝对路径原样,相对路径基于工作区根。 */
    private Path resolveUnrestricted(String cleaned) {
        Path candidate;
        if (cleaned.equals("~") || cleaned.startsWith("~/")) {
            String rest = cleaned.equals("~") ? "" : cleaned.substring(2);
            candidate = Path.of(System.getProperty("user.home")).resolve(rest).normalize();
        } else {
            Path given = Path.of(cleaned);
            candidate = given.isAbsolute() ? given.normalize() : root.resolve(cleaned).normalize();
        }
        if (Files.exists(candidate)) {
            try {
                return candidate.toRealPath();
            } catch (IOException e) {
                return candidate;
            }
        }
        return candidate;
    }

    /** 词法归属检查:落在主根返回主根,落在第二根返回第二根,均否返回 null。 */
    private Path boundaryOf(Path normalized) {
        if (normalized.startsWith(root)) {
            return root;
        }
        if (secondary != null && normalized.startsWith(secondary)) {
            return secondary;
        }
        return null;
    }

    /** 相对根的展示路径（回给模型/前端时用）：第二根内的路径以 ~/ 前缀呈现。 */
    public String relativize(Path absolute) {
        if (absolute.startsWith(root)) {
            return root.relativize(absolute).toString();
        }
        if (secondary != null && absolute.startsWith(secondary)) {
            return "~/" + secondary.relativize(absolute);
        }
        return absolute.toString();
    }
}
