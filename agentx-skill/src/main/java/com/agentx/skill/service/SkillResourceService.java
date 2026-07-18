package com.agentx.skill.service;

import com.agentx.skill.store.SkillFileStore;
import com.agentx.skill.store.SkillProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Skill L3 资源（references/ scripts/ assets/…）：清单公告 + 沙箱读取。
 * 对标 Claude Code 的渐进式披露第三层——Claude Code 靠模型自带的 Read 工具 +
 * 基准目录公告实现;AgentX 的聊天模型没有通用文件读取手,此处提供限定在
 * 技能目录内的等价能力（readSkillFile 工具的实现）。
 */
@Slf4j
@Service
public class SkillResourceService {

    /** 单文件读取上限（字符）:超出保头截断并显式标注。 */
    private static final int MAX_CHARS = 50_000;
    /** 清单条目上限。 */
    private static final int MAX_ENTRIES = 50;

    private final Path localRoot;
    private final List<SkillProvider> providers;

    public SkillResourceService(@Value("${agentx.skills.root}") String localRoot,
                                List<SkillProvider> providers) {
        this.localRoot = Path.of(localRoot);
        this.providers = providers;
    }

    /** 技能的资源基准目录:本地目录布局技能 → <root>/<name>/;插件技能问各 provider。 */
    public Optional<Path> baseDir(String name) {
        if (!name.contains(":")) {
            Path dir = localRoot.resolve(name);
            return Files.isDirectory(dir) && SkillFileStore.NAME_PATTERN.matcher(name).matches()
                    ? Optional.of(dir) : Optional.empty();
        }
        for (SkillProvider provider : providers) {
            Optional<Path> dir = provider.resourceDir(name);
            if (dir.isPresent()) {
                return dir;
            }
        }
        return Optional.empty();
    }

    /** 资源清单（相对路径,不含 SKILL.md 本体;空清单=无资源）。 */
    public List<String> listResources(String name) {
        Path base = baseDir(name).orElse(null);
        if (base == null) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(base, 4)) {
            for (Path p : walk.sorted().toList()) {
                if (!Files.isRegularFile(p) || Files.isSymbolicLink(p)) {
                    continue;
                }
                String rel = base.relativize(p).toString();
                if (rel.equals("SKILL.md") || rel.startsWith(".")) {
                    continue;
                }
                result.add(rel);
                if (result.size() >= MAX_ENTRIES) {
                    break;
                }
            }
        } catch (IOException e) {
            log.warn("技能资源清单扫描失败: {}", base, e);
        }
        return result;
    }

    /** 沙箱读取:路径必须落在技能目录内(拦截穿越/符号链接),超限保头截断。 */
    public String read(String name, String relPath) {
        Path base = baseDir(name).orElse(null);
        if (base == null) {
            return "技能不存在或没有资源目录: " + name;
        }
        Path target = base.resolve(relPath).normalize();
        if (!target.startsWith(base)) {
            return "非法路径(越界): " + relPath;
        }
        try {
            if (!Files.isRegularFile(target) || Files.isSymbolicLink(target)
                    || !target.toRealPath().startsWith(base.toRealPath())) {
                return "文件不存在: " + relPath + "(可用文件见技能加载时的资源清单)";
            }
            String content = Files.readString(target);
            if (content.length() > MAX_CHARS) {
                return content.substring(0, MAX_CHARS)
                        + "\n\n[文件已截断:全文 " + content.length() + " 字符,此处仅含前 " + MAX_CHARS + " 字符]";
            }
            return content;
        } catch (IOException e) {
            return "读取失败: " + relPath + "(" + e.getMessage() + ")";
        }
    }

    /** 资源清单公告文本（附在技能 body 之后;无资源返回空串）。 */
    public String resourcesNote(String name) {
        List<String> resources = listResources(name);
        if (resources.isEmpty()) {
            return "";
        }
        return "\n\n本技能目录附带以下资源文件,指令中引用到时用 readSkillFile 工具按需读取（skill=\""
                + name + "\"）:\n- " + String.join("\n- ", resources);
    }
}
