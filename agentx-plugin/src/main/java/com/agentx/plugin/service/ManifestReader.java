package com.agentx.plugin.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 解析 Claude Code 同款 manifest：
 * <li>&lt;dir&gt;/.claude-plugin/marketplace.json —— marketplace 清单(plugins[].source 支持相对路径/github/url)
 * <li>&lt;dir&gt;/.claude-plugin/plugin.json —— 插件元数据
 * 没有 marketplace.json 但有 plugin.json 的仓库视作「单插件直装仓库」,合成单条目清单。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ManifestReader {

    private final ObjectMapper objectMapper;

    /** marketplace 清单(或单插件仓库的合成清单);name/条目均已校验命名。 */
    public record Marketplace(String name, String description, List<Entry> plugins) {}

    /**
     * 插件条目。sourceType: relative|github|url;locator 相应为相对路径/owner-repo/git url。
     */
    public record Entry(String name, String description, String version,
                        String sourceType, String locator) {}

    public record PluginMeta(String name, String description, String version) {}

    public Optional<Marketplace> readMarketplace(Path dir) {
        JsonNode node = readJson(dir.resolve(".claude-plugin").resolve("marketplace.json"));
        if (node != null) {
            List<Entry> entries = new ArrayList<>();
            for (JsonNode p : node.path("plugins")) {
                Entry entry = toEntry(p);
                if (entry != null) {
                    entries.add(entry);
                }
            }
            return Optional.of(new Marketplace(
                    node.path("name").asString(""),
                    node.path("description").asString(""),
                    entries));
        }
        // 单插件直装仓库:根下（或 .claude-plugin/）有 plugin.json
        return readPluginMeta(dir).map(meta -> new Marketplace(
                meta.name(), meta.description(),
                List.of(new Entry(meta.name(), meta.description(), meta.version(), "relative", "."))));
    }

    public Optional<PluginMeta> readPluginMeta(Path pluginDir) {
        JsonNode node = readJson(pluginDir.resolve(".claude-plugin").resolve("plugin.json"));
        if (node == null) {
            return Optional.empty();
        }
        String name = node.path("name").asString("");
        return name.isEmpty() ? Optional.empty() : Optional.of(new PluginMeta(
                name,
                node.path("description").asString(""),
                node.path("version").asString("")));
    }

    private Entry toEntry(JsonNode p) {
        String name = p.path("name").asString("");
        if (name.isEmpty()) {
            return null;
        }
        String description = p.path("description").asString("");
        String version = p.path("version").asString("");
        JsonNode source = p.path("source");
        if (source.isString()) {
            return new Entry(name, description, version, "relative", source.asString(""));
        }
        String type = source.path("source").asString("");
        return switch (type) {
            case "github" -> new Entry(name, description, version, "github", source.path("repo").asString(""));
            case "url" -> new Entry(name, description, version, "url", source.path("url").asString(""));
            default -> {
                log.debug("插件 {} 的 source 类型暂不支持: {}", name, type);
                yield new Entry(name, description, version, "unsupported", type);
            }
        };
    }

    private JsonNode readJson(Path file) {
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try {
            return objectMapper.readTree(Files.readString(file));
        } catch (IOException | RuntimeException e) {
            log.warn("manifest 解析失败: {}", file, e);
            return null;
        }
    }
}
