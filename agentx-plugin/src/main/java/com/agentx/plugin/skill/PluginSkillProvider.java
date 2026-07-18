package com.agentx.plugin.skill;

import com.agentx.plugin.store.InstalledPlugin;
import com.agentx.plugin.store.PluginRegistry;
import com.agentx.skill.store.SkillFile;
import com.agentx.skill.store.SkillMarkdown;
import com.agentx.skill.store.SkillProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 把启用插件的 skills 挂进 skill 模块的列表与展开链路（实现 SkillProvider SPI）。
 * 扫描 Claude Code 插件默认布局:skills/&lt;dir&gt;/SKILL.md(dir 名即 skill 名)与
 * commands/&lt;name&gt;.md;全限定名 "&lt;plugin&gt;:&lt;skill&gt;"。skill 名以目录/文件名为准。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PluginSkillProvider implements SkillProvider {

    private static final Pattern SKILL_NAME = Pattern.compile("^[a-z0-9-]{1,64}$");

    private final PluginRegistry registry;

    @Override
    public List<SkillFile> list() {
        List<SkillFile> result = new ArrayList<>();
        for (InstalledPlugin plugin : registry.plugins().values()) {
            if (!plugin.enabled()) {
                continue;
            }
            Path root = Path.of(plugin.installPath());
            Path skillsDir = root.resolve("skills");
            if (Files.isDirectory(skillsDir)) {
                try (Stream<Path> entries = Files.list(skillsDir)) {
                    for (Path dir : entries.sorted().toList()) {
                        Path md = dir.resolve("SKILL.md");
                        if (Files.isRegularFile(md)) {
                            parse(plugin.name(), dir.getFileName().toString(), md)
                                    .ifPresent(result::add);
                        }
                    }
                } catch (IOException e) {
                    log.warn("插件 skills 扫描失败: {}", skillsDir, e);
                }
            }
            Path commandsDir = root.resolve("commands");
            if (Files.isDirectory(commandsDir)) {
                try (Stream<Path> entries = Files.list(commandsDir)) {
                    for (Path file : entries.sorted().toList()) {
                        String fileName = file.getFileName().toString();
                        if (Files.isRegularFile(file) && fileName.endsWith(".md")) {
                            parse(plugin.name(), fileName.substring(0, fileName.length() - 3), file)
                                    .ifPresent(result::add);
                        }
                    }
                } catch (IOException e) {
                    log.warn("插件 commands 扫描失败: {}", commandsDir, e);
                }
            }
        }
        return result;
    }

    @Override
    public Optional<SkillFile> find(String name) {
        int colon = name.indexOf(':');
        if (colon <= 0) {
            return Optional.empty();
        }
        String pluginName = name.substring(0, colon);
        String skillName = name.substring(colon + 1);
        InstalledPlugin plugin = registry.plugins().values().stream()
                .filter(p -> p.enabled() && p.name().equals(pluginName))
                .findFirst().orElse(null);
        if (plugin == null || !SKILL_NAME.matcher(skillName).matches()) {
            return Optional.empty();
        }
        Path root = Path.of(plugin.installPath());
        Path dirLayout = root.resolve("skills").resolve(skillName).resolve("SKILL.md");
        if (Files.isRegularFile(dirLayout)) {
            return parse(pluginName, skillName, dirLayout);
        }
        Path command = root.resolve("commands").resolve(skillName + ".md");
        return Files.isRegularFile(command) ? parse(pluginName, skillName, command) : Optional.empty();
    }

    @Override
    public Optional<java.nio.file.Path> resourceDir(String name) {
        int colon = name.indexOf(':');
        if (colon <= 0) {
            return Optional.empty();
        }
        String pluginName = name.substring(0, colon);
        String skillName = name.substring(colon + 1);
        InstalledPlugin plugin = registry.plugins().values().stream()
                .filter(p -> p.enabled() && p.name().equals(pluginName))
                .findFirst().orElse(null);
        if (plugin == null || !SKILL_NAME.matcher(skillName).matches()) {
            return Optional.empty();
        }
        Path dir = Path.of(plugin.installPath()).resolve("skills").resolve(skillName);
        return Files.isDirectory(dir) ? Optional.of(dir) : Optional.empty();
    }

    private Optional<SkillFile> parse(String pluginName, String skillName, Path file) {
        if (!SKILL_NAME.matcher(skillName).matches()) {
            log.debug("跳过非法命名的插件 skill: {}", file);
            return Optional.empty();
        }
        try {
            SkillFile parsed = SkillMarkdown.parse(pluginName + ":" + skillName,
                    Files.readString(file), Files.getLastModifiedTime(file).toInstant());
            return parsed.enabled() ? Optional.of(parsed) : Optional.empty();
        } catch (IOException e) {
            log.warn("插件 skill 读取失败: {}", file, e);
            return Optional.empty();
        }
    }
}
