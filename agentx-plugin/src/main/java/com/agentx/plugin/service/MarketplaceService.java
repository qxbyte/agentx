package com.agentx.plugin.service;

import com.agentx.common.api.ErrorCode;
import com.agentx.common.exception.BizException;
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
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Marketplace 管理：添加(github owner/repo | git https URL | 本地绝对路径)、列表、移除。
 * git 来源 clone 到 &lt;root&gt;/marketplaces/&lt;name&gt;/;本地路径不 clone 直接引用。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketplaceService {

    public static final Pattern NAME_PATTERN = Pattern.compile("^[a-z0-9-]{1,64}$");
    private static final Pattern GITHUB_REPO = Pattern.compile("^[\\w.-]+/[\\w.-]+$");

    private final PluginRegistry registry;
    private final GitFetcher git;
    private final ManifestReader manifests;

    /** 添加 marketplace，返回注册名。 */
    public String add(String source) {
        String s = source.strip();
        if (s.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "来源不能为空");
        }
        if (s.startsWith("/")) {
            return addLocalPath(Path.of(s));
        }
        if (GITHUB_REPO.matcher(s).matches()) {
            return addGit("github", s, "https://github.com/" + s + ".git");
        }
        if (s.startsWith("https://") || s.startsWith("http://")) {
            return addGit("url", s, s);
        }
        throw new BizException(ErrorCode.BAD_REQUEST,
                "无法识别的来源，请输入 owner/repo、git https URL 或本地绝对路径");
    }

    private String addLocalPath(Path dir) {
        if (!Files.isDirectory(dir)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "本地路径不存在: " + dir);
        }
        var manifest = manifests.readMarketplace(dir).orElseThrow(() -> new BizException(
                ErrorCode.BAD_REQUEST, "该目录不含 .claude-plugin/marketplace.json 或 plugin.json"));
        String name = requireValidName(manifest.name());
        registry.putMarketplace(name, new KnownMarketplace(
                "path", dir.toString(), dir.toString(), Instant.now().toString()));
        return name;
    }

    private String addGit(String type, String locator, String cloneUrl) {
        Path temp = createTemp();
        git.cloneShallow(cloneUrl, temp);
        var manifest = manifests.readMarketplace(temp).orElseThrow(() -> new BizException(
                ErrorCode.BAD_REQUEST, "该仓库不含 .claude-plugin/marketplace.json 或 plugin.json"));
        String name = requireValidName(manifest.name());
        Path target = registry.marketplacesDir().resolve(name);
        try {
            deleteRecursively(target);
            Files.createDirectories(target.getParent());
            Files.move(temp, target);
        } catch (IOException e) {
            throw new UncheckedIOException("marketplace 落盘失败: " + name, e);
        }
        registry.putMarketplace(name, new KnownMarketplace(
                type, locator, target.toString(), Instant.now().toString()));
        return name;
    }

    public Map<String, KnownMarketplace> list() {
        return registry.marketplaces();
    }

    public KnownMarketplace getOrThrow(String name) {
        KnownMarketplace mp = registry.marketplaces().get(name);
        if (mp == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "marketplace 不存在: " + name);
        }
        return mp;
    }

    public Optional<ManifestReader.Marketplace> manifest(String name) {
        return manifests.readMarketplace(Path.of(getOrThrow(name).installLocation()));
    }

    public void remove(String name) {
        KnownMarketplace mp = getOrThrow(name);
        // 只删自己 clone 的目录;本地路径来源仅解除注册
        Path location = Path.of(mp.installLocation());
        if (location.startsWith(registry.marketplacesDir())) {
            try {
                deleteRecursively(location);
            } catch (IOException e) {
                throw new UncheckedIOException("删除 marketplace 目录失败: " + name, e);
            }
        }
        registry.removeMarketplace(name);
    }

    private String requireValidName(String name) {
        if (name == null || !NAME_PATTERN.matcher(name).matches()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "marketplace 名称非法: " + name);
        }
        return name;
    }

    private Path createTemp() {
        try {
            return Files.createTempDirectory("agentx-mp-");
        } catch (IOException e) {
            throw new UncheckedIOException("创建临时目录失败", e);
        }
    }

    static void deleteRecursively(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(p);
            }
        }
    }
}
