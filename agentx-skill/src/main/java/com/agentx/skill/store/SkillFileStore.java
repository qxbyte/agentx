package com.agentx.skill.store;

import com.agentx.common.api.ErrorCode;
import com.agentx.common.exception.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Skill 文件存储（对标 Claude Code / Codex 的本地目录化配置，Codex 语义：
 * skill 属于本机而非账号——账号只同步对话历史，skill/插件本地目录化）：
 * <pre>
 * &lt;root&gt;/&lt;name&gt;.md            平铺布局（API 创建的默认形态，手动放文件也最顺手）
 * &lt;root&gt;/&lt;name&gt;/SKILL.md      目录布局（预留 scripts/references 等资源，优先级更高）
 * </pre>
 * 文件即唯一事实源：每次列表/查找都实时读盘，手动增删改文件即时生效，无需重启。
 * 文件格式：YAML frontmatter（description / argument-hint / enabled）+ markdown body。
 */
@Slf4j
@Component
public class SkillFileStore {

    public static final Pattern NAME_PATTERN = Pattern.compile("^[a-z0-9-]{1,64}$");
    private final Path root;

    public SkillFileStore(@Value("${agentx.skills.root}") String root) {
        this.root = Path.of(root);
    }

    /** 扫描全部 skill（目录布局优先于平铺同名文件），按 name 排序。 */
    public List<SkillFile> scan() {
        Path dir = root;
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        // TreeMap 按 name 去重 + 排序；先收平铺文件，再让目录布局覆盖同名项
        TreeMap<String, SkillFile> byName = new TreeMap<>();
        try (Stream<Path> entries = Files.list(dir)) {
            List<Path> children = entries.toList();
            for (Path child : children) {
                String fileName = child.getFileName().toString();
                if (Files.isRegularFile(child) && fileName.endsWith(".md")) {
                    String name = fileName.substring(0, fileName.length() - 3);
                    parse(name, child).ifPresent(s -> byName.put(name, s));
                }
            }
            for (Path child : children) {
                Path skillMd = child.resolve("SKILL.md");
                if (Files.isDirectory(child) && Files.isRegularFile(skillMd)) {
                    String name = child.getFileName().toString();
                    parse(name, skillMd).ifPresent(s -> byName.put(name, s));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("扫描 skill 目录失败: " + dir, e);
        }
        return new ArrayList<>(byName.values());
    }

    public Optional<SkillFile> find(String name) {
        if (!NAME_PATTERN.matcher(name).matches()) {
            return Optional.empty();
        }
        Path file = resolveExisting(name);
        return file == null ? Optional.empty() : parse(name, file);
    }

    public boolean exists(String name) {
        return resolveExisting(name) != null;
    }

    /** 写入（创建或覆盖）：已有目录布局则写回其 SKILL.md，否则写平铺 <name>.md。 */
    public void write(SkillFile skill) {
        requireValidName(skill.name());
        Path dir = root;
        try {
            Files.createDirectories(dir);
            Path dirLayout = dir.resolve(skill.name()).resolve("SKILL.md");
            Path target = Files.isRegularFile(dirLayout) ? dirLayout : dir.resolve(skill.name() + ".md");
            Files.writeString(target, serialize(skill));
        } catch (IOException e) {
            throw new UncheckedIOException("写入 skill 文件失败: " + skill.name(), e);
        }
    }

    /** 删除：平铺文件直接删；目录布局递归删除整个 skill 目录。 */
    public void delete(String name) {
        requireValidName(name);
        Path dir = root;
        try {
            Path flat = dir.resolve(name + ".md");
            Files.deleteIfExists(flat);
            Path skillDir = dir.resolve(name);
            if (Files.isDirectory(skillDir)) {
                try (Stream<Path> walk = Files.walk(skillDir)) {
                    for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) {
                        Files.deleteIfExists(p);
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("删除 skill 文件失败: " + name, e);
        }
    }

    public long count() {
        return scan().size();
    }

    /* ---------- 内部 ---------- */

    private void requireValidName(String name) {
        if (name == null || !NAME_PATTERN.matcher(name).matches()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "技能名称仅允许小写字母、数字、连字符，长度 1-64");
        }
    }

    private Path resolveExisting(String name) {
        Path dir = root;
        Path dirLayout = dir.resolve(name).resolve("SKILL.md");
        if (Files.isRegularFile(dirLayout)) {
            return dirLayout;
        }
        Path flat = dir.resolve(name + ".md");
        return Files.isRegularFile(flat) ? flat : null;
    }

    private Optional<SkillFile> parse(String name, Path file) {
        if (!NAME_PATTERN.matcher(name).matches()) {
            log.debug("跳过非法命名的 skill 文件: {}", file);
            return Optional.empty();
        }
        try {
            return Optional.of(SkillMarkdown.parse(name,
                    Files.readString(file), Files.getLastModifiedTime(file).toInstant()));
        } catch (IOException e) {
            log.warn("读取 skill 文件失败，跳过: {}", file, e);
            return Optional.empty();
        }
    }

    private String serialize(SkillFile skill) {
        return SkillMarkdown.serialize(skill);
    }
}
