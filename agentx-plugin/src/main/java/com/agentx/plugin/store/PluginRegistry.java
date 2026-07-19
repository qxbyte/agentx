package com.agentx.plugin.store;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 插件注册表：~/.agentx/plugins/ 下两个 JSON 文件的读写
 * （~/.agentx/plugins/ 目录,enabled 状态内嵌 installed_plugins.json）。
 * 文件即事实源:每次读盘,外部手动编辑即时生效;写操作 synchronized 防并发覆写。
 */
@Slf4j
@Component
public class PluginRegistry {

    private final Path root;
    private final ObjectMapper objectMapper;

    public PluginRegistry(@Value("${agentx.plugins.root}") String root, ObjectMapper objectMapper) {
        this.root = Path.of(root);
        this.objectMapper = objectMapper;
    }

    public Path root() {
        return root;
    }

    public Path marketplacesDir() {
        return root.resolve("marketplaces");
    }

    public Path cacheDir() {
        return root.resolve("cache");
    }

    /* ---------- known_marketplaces.json ---------- */

    public Map<String, KnownMarketplace> marketplaces() {
        return read(root.resolve("known_marketplaces.json"),
                new TypeReference<LinkedHashMap<String, KnownMarketplace>>() {});
    }

    public synchronized void putMarketplace(String name, KnownMarketplace record) {
        Map<String, KnownMarketplace> all = marketplaces();
        all.put(name, record);
        write(root.resolve("known_marketplaces.json"), all);
    }

    public synchronized void removeMarketplace(String name) {
        Map<String, KnownMarketplace> all = marketplaces();
        all.remove(name);
        write(root.resolve("known_marketplaces.json"), all);
    }

    /* ---------- installed_plugins.json ---------- */

    public Map<String, InstalledPlugin> plugins() {
        return read(root.resolve("installed_plugins.json"),
                new TypeReference<LinkedHashMap<String, InstalledPlugin>>() {});
    }

    public synchronized void putPlugin(InstalledPlugin plugin) {
        Map<String, InstalledPlugin> all = plugins();
        all.put(plugin.id(), plugin);
        write(root.resolve("installed_plugins.json"), all);
    }

    public synchronized void removePlugin(String id) {
        Map<String, InstalledPlugin> all = plugins();
        all.remove(id);
        write(root.resolve("installed_plugins.json"), all);
    }

    /* ---------- 内部 ---------- */

    private <T> Map<String, T> read(Path file, TypeReference<LinkedHashMap<String, T>> type) {
        if (!Files.isRegularFile(file)) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(Files.readString(file), type);
        } catch (IOException | RuntimeException e) {
            log.warn("读取注册表失败，按空处理: {}", file, e);
            return new LinkedHashMap<>();
        }
    }

    private void write(Path file, Object value) {
        try {
            Files.createDirectories(root);
            Files.writeString(file, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value));
        } catch (IOException e) {
            throw new UncheckedIOException("写入注册表失败: " + file, e);
        }
    }
}
