package com.agentx.plugin.service;

import com.agentx.agent.service.PluginAgentRegistry;
import com.agentx.mcp.domain.McpServerConfig;
import com.agentx.mcp.service.PluginMcpRegistry;
import com.agentx.common.api.ErrorCode;
import com.agentx.common.exception.BizException;
import com.agentx.skill.store.SkillMarkdown;
import com.agentx.plugin.store.InstalledPlugin;
import com.agentx.plugin.store.KnownMarketplace;
import com.agentx.plugin.store.PluginRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * 插件安装/启停/卸载。安装副本落 cache/&lt;marketplace&gt;/&lt;plugin&gt;/&lt;version&gt;/
 * （对齐 Claude Code 三级布局）;拷贝跳过 .git 与符号链接。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PluginService {

    private final PluginRegistry registry;
    private final MarketplaceService marketplaces;
    private final ManifestReader manifests;
    private final GitFetcher git;
    private final PluginAgentRegistry agentRegistry;
    private final PluginMcpRegistry mcpRegistry;
    private final tools.jackson.databind.ObjectMapper objectMapper;

    public InstalledPlugin install(String name, String marketplaceName) {
        KnownMarketplace marketplace = marketplaces.getOrThrow(marketplaceName);
        var manifest = marketplaces.manifest(marketplaceName).orElseThrow(() -> new BizException(
                ErrorCode.BAD_REQUEST, "marketplace 清单不可读: " + marketplaceName));
        var entry = manifest.plugins().stream()
                .filter(p -> p.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND,
                        "marketplace " + marketplaceName + " 中没有插件 " + name));
        if (!MarketplaceService.NAME_PATTERN.matcher(name).matches()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "插件名称非法: " + name);
        }

        // 解析 source 得到插件源目录
        Path sourceDir;
        Path gitShaDir;
        switch (entry.sourceType()) {
            case "relative" -> {
                Path base = Path.of(marketplace.installLocation());
                sourceDir = base.resolve(entry.locator()).normalize();
                if (!sourceDir.startsWith(base)) {
                    throw new BizException(ErrorCode.BAD_REQUEST, "插件路径越界: " + entry.locator());
                }
                gitShaDir = base;
            }
            case "github" -> {
                sourceDir = cloneTemp("https://github.com/" + entry.locator() + ".git");
                gitShaDir = sourceDir;
            }
            case "url" -> {
                sourceDir = cloneTemp(entry.locator());
                gitShaDir = sourceDir;
            }
            default -> throw new BizException(ErrorCode.BAD_REQUEST,
                    "该插件的 source 类型暂不支持: " + entry.sourceType());
        }
        if (!Files.isDirectory(sourceDir)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "插件源目录不存在: " + sourceDir);
        }

        // 版本优先级:plugin.json > marketplace 条目 > unknown(对齐 Claude Code 实测)
        String version = manifests.readPluginMeta(sourceDir)
                .map(ManifestReader.PluginMeta::version)
                .filter(v -> !v.isBlank())
                .orElse(entry.version() == null || entry.version().isBlank() ? "unknown" : entry.version());

        Path target = registry.cacheDir().resolve(marketplaceName).resolve(name).resolve(version);
        try {
            MarketplaceService.deleteRecursively(registry.cacheDir().resolve(marketplaceName).resolve(name));
            copyTree(sourceDir, target);
        } catch (IOException e) {
            throw new UncheckedIOException("插件拷贝失败: " + name, e);
        }

        InstalledPlugin installed = new InstalledPlugin(name, marketplaceName, target.toString(),
                version, true, Instant.now().toString(), git.headSha(gitShaDir));
        registry.putPlugin(installed);
        // 插件 agents/*.md 同步为只读 Agent 定义(source=PLUGIN,随插件生命周期联动)
        agentRegistry.sync(installed.id(), parseAgents(installed), true);
        // 插件 .mcp.json 同步为 MCP 配置(默认停用,用户显式启用=信任边界)
        mcpRegistry.sync(installed.id(), parseMcpServers(installed));
        log.info("插件已安装 {}@{} version={} path={}", name, marketplaceName, version, target);
        return installed;
    }

    public Map<String, InstalledPlugin> list() {
        return registry.plugins();
    }

    public InstalledPlugin getOrThrow(String id) {
        InstalledPlugin plugin = registry.plugins().get(id);
        if (plugin == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "插件未安装: " + id);
        }
        return plugin;
    }

    public InstalledPlugin setEnabled(String id, boolean enabled) {
        InstalledPlugin updated = getOrThrow(id).withEnabled(enabled);
        registry.putPlugin(updated);
        agentRegistry.setEnabled(id, enabled);
        if (!enabled) {
            mcpRegistry.disableAll(id);
        }
        return updated;
    }

    /** 更新插件:先刷新其 marketplace(git pull),再重装(重拷贝+agents 重同步),保留启停状态。 */
    public InstalledPlugin update(String id) {
        InstalledPlugin current = getOrThrow(id);
        marketplaces.update(current.marketplace());
        InstalledPlugin fresh = install(current.name(), current.marketplace());
        if (!current.enabled()) {
            fresh = setEnabled(id, false);
        }
        log.info("插件已更新 {} {} -> {}", id, current.version(), fresh.version());
        return fresh;
    }

    public void uninstall(String id) {
        InstalledPlugin plugin = getOrThrow(id);
        try {
            // 删整个 cache/<marketplace>/<name>(含历史版本目录)
            MarketplaceService.deleteRecursively(
                    registry.cacheDir().resolve(plugin.marketplace()).resolve(plugin.name()));
        } catch (IOException e) {
            throw new UncheckedIOException("删除插件目录失败: " + id, e);
        }
        registry.removePlugin(id);
        agentRegistry.remove(id);
        mcpRegistry.remove(id);
    }

    /* ---------- 内部 ---------- */

    private Path cloneTemp(String url) {
        try {
            Path temp = Files.createTempDirectory("agentx-plugin-");
            git.cloneShallow(url, temp);
            return temp;
        } catch (IOException e) {
            throw new UncheckedIOException("创建临时目录失败", e);
        }
    }

    /** 递归拷贝,跳过 .git 与符号链接。 */
    private static void copyTree(Path from, Path to) throws IOException {
        try (Stream<Path> walk = Files.walk(from)) {
            for (Path src : walk.toList()) {
                if (Files.isSymbolicLink(src) || src.getFileName().toString().equals(".git")
                        || pathContains(from.relativize(src), ".git")) {
                    continue;
                }
                Path dst = to.resolve(from.relativize(src).toString());
                if (Files.isDirectory(src)) {
                    Files.createDirectories(dst);
                } else {
                    Files.createDirectories(dst.getParent());
                    Files.copy(src, dst);
                }
            }
        }
    }

    private static boolean pathContains(Path relative, String segment) {
        for (Path part : relative) {
            if (part.toString().equals(segment)) {
                return true;
            }
        }
        return false;
    }

    /** 能力盘点:skills/agents/MCP 数与暂不支持的能力名单(hooks),供列表展示。 */
    public record Capabilities(int skillCount, int agentCount, int mcpCount, List<String> unsupported) {}

    public Capabilities capabilities(InstalledPlugin plugin) {
        Path root = Path.of(plugin.installPath());
        List<String> unsupported = new java.util.ArrayList<>();
        if (Files.isRegularFile(root.resolve("hooks").resolve("hooks.json"))) unsupported.add("hooks");
        return new Capabilities(countSkills(root), parseAgents(plugin).size(),
                parseMcpServers(plugin).size(), List.copyOf(unsupported));
    }

    /**
     * 解析插件 .mcp.json（Claude Code 同款:mcpServers 映射,command/args/env → STDIO,
     * url/headers → STREAMABLE_HTTP）→ 命名空间化的 MCP 规格。
     */
    private List<PluginMcpRegistry.PluginMcpSpec> parseMcpServers(InstalledPlugin plugin) {
        Path file = Path.of(plugin.installPath()).resolve(".mcp.json");
        if (!Files.isRegularFile(file)) {
            return List.of();
        }
        List<PluginMcpRegistry.PluginMcpSpec> specs = new java.util.ArrayList<>();
        try {
            var rootNode = objectMapper.readTree(Files.readString(file));
            var servers = rootNode.path("mcpServers");
            servers.propertyNames().forEach(serverName -> {
                if (!MarketplaceService.NAME_PATTERN.matcher(serverName).matches()) {
                    log.warn("插件 MCP server 命名非法,跳过: {}", serverName);
                    return;
                }
                var node = servers.path(serverName);
                String qualified = plugin.name() + ":" + serverName;
                if (node.has("command")) {
                    specs.add(new PluginMcpRegistry.PluginMcpSpec(qualified,
                            McpServerConfig.Transport.STDIO, objectMapper.writeValueAsString(node)));
                } else if (node.has("url")) {
                    specs.add(new PluginMcpRegistry.PluginMcpSpec(qualified,
                            McpServerConfig.Transport.STREAMABLE_HTTP, objectMapper.writeValueAsString(node)));
                }
            });
        } catch (IOException | RuntimeException e) {
            log.warn("插件 .mcp.json 解析失败: {}", file, e);
        }
        return specs;
    }

    /**
     * 解析插件 agents/*.md（Claude Code 子代理定义:frontmatter description + body 即
     * system prompt）→ 命名空间化的 Agent 规格。model/tools 等 Claude 专属字段忽略。
     */
    private List<PluginAgentRegistry.PluginAgentSpec> parseAgents(InstalledPlugin plugin) {
        Path dir = Path.of(plugin.installPath()).resolve("agents");
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        List<PluginAgentRegistry.PluginAgentSpec> specs = new java.util.ArrayList<>();
        try (Stream<Path> entries = Files.list(dir)) {
            for (Path file : entries.sorted().toList()) {
                String fileName = file.getFileName().toString();
                if (!Files.isRegularFile(file) || !fileName.endsWith(".md")) {
                    continue;
                }
                String agentName = fileName.substring(0, fileName.length() - 3);
                if (!MarketplaceService.NAME_PATTERN.matcher(agentName).matches()) {
                    continue;
                }
                var parsed = SkillMarkdown.parse(agentName,
                        Files.readString(file), Instant.now());
                specs.add(new PluginAgentRegistry.PluginAgentSpec(
                        plugin.name() + ":" + agentName, parsed.description(), parsed.content()));
            }
        } catch (IOException e) {
            log.warn("插件 agents 扫描失败: {}", dir, e);
        }
        return specs;
    }

    private static int countSkills(Path root) {
        int count = 0;
        Path skillsDir = root.resolve("skills");
        if (Files.isDirectory(skillsDir)) {
            try (Stream<Path> entries = Files.list(skillsDir)) {
                count += (int) entries.filter(p -> Files.isRegularFile(p.resolve("SKILL.md"))).count();
            } catch (IOException e) {
                log.warn("skills 目录扫描失败: {}", skillsDir, e);
            }
        }
        Path commandsDir = root.resolve("commands");
        if (Files.isDirectory(commandsDir)) {
            try (Stream<Path> entries = Files.list(commandsDir)) {
                count += (int) entries.filter(p -> p.getFileName().toString().endsWith(".md")).count();
            } catch (IOException e) {
                log.warn("commands 目录扫描失败: {}", commandsDir, e);
            }
        }
        return count;
    }
}
