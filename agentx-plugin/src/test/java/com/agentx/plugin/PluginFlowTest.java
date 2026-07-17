package com.agentx.plugin;

import com.agentx.plugin.service.GitFetcher;
import com.agentx.plugin.service.ManifestReader;
import com.agentx.plugin.service.MarketplaceService;
import com.agentx.plugin.service.PluginService;
import com.agentx.plugin.skill.PluginSkillProvider;
import com.agentx.plugin.store.PluginRegistry;
import com.agentx.skill.store.SkillFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.assertThat;

/** 本地路径 marketplace 的完整流程:add → install → skills 进 provider → 停用/卸载。不依赖网络与 git。 */
class PluginFlowTest {

    @TempDir
    Path pluginsRoot;
    @TempDir
    Path marketplaceDir;

    private MarketplaceService marketplaces;
    private PluginService plugins;
    private PluginSkillProvider provider;

    @BeforeEach
    void setUp() throws IOException {
        // 构造一个本地 marketplace:.claude-plugin/marketplace.json + plugins/demo(含 skills 与 commands)
        write(marketplaceDir.resolve(".claude-plugin/marketplace.json"), """
                {"name":"local-mp","owner":{"name":"test"},
                 "plugins":[{"name":"demo","source":"./plugins/demo","version":"1.2.0",
                             "description":"演示插件"}]}""");
        write(marketplaceDir.resolve("plugins/demo/.claude-plugin/plugin.json"),
                "{\"name\":\"demo\",\"description\":\"演示插件\",\"version\":\"1.2.0\"}");
        write(marketplaceDir.resolve("plugins/demo/skills/brainstorm/SKILL.md"), """
                ---
                description: 头脑风暴
                ---

                和用户一起头脑风暴：$ARGUMENTS""");
        write(marketplaceDir.resolve("plugins/demo/commands/ship.md"), "发布流程：$1");
        write(marketplaceDir.resolve("plugins/demo/hooks/hooks.json"), "{}");

        ObjectMapper om = new ObjectMapper();
        PluginRegistry registry = new PluginRegistry(pluginsRoot.toString(), om);
        ManifestReader manifests = new ManifestReader(om);
        marketplaces = new MarketplaceService(registry, new GitFetcher(), manifests);
        plugins = new PluginService(registry, marketplaces, manifests, new GitFetcher());
        provider = new PluginSkillProvider(registry);
    }

    private static void write(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    @Test
    void fullLifecycle() {
        // add 本地路径 marketplace
        String name = marketplaces.add(marketplaceDir.toString());
        assertThat(name).isEqualTo("local-mp");
        assertThat(marketplaces.manifest("local-mp").orElseThrow().plugins())
                .extracting(ManifestReader.Entry::name).containsExactly("demo");

        // install:版本取 plugin.json,落 cache 三级目录
        var installed = plugins.install("demo", "local-mp");
        assertThat(installed.version()).isEqualTo("1.2.0");
        assertThat(Path.of(installed.installPath()))
                .isEqualTo(pluginsRoot.resolve("cache/local-mp/demo/1.2.0"));
        assertThat(Path.of(installed.installPath()).resolve("skills/brainstorm/SKILL.md")).exists();

        // 能力盘点:2 个 skill(skills+commands),暂不支持名单 = [hooks]
        var caps = plugins.capabilities(installed);
        assertThat(caps.skillCount()).isEqualTo(2);
        assertThat(caps.unsupported()).containsExactly("hooks");

        // provider:命名空间 skill 可列出、可查找
        assertThat(provider.list()).extracting(SkillFile::name)
                .containsExactlyInAnyOrder("demo:brainstorm", "demo:ship");
        assertThat(provider.find("demo:brainstorm").orElseThrow().description()).isEqualTo("头脑风暴");
        assertThat(provider.find("demo:ship").orElseThrow().content()).contains("$1");

        // 停用 → provider 不再供给
        plugins.setEnabled("demo@local-mp", false);
        assertThat(provider.list()).isEmpty();
        assertThat(provider.find("demo:brainstorm")).isEmpty();

        // 重新启用 → 恢复;卸载 → cache 清空 + 注册表干净
        plugins.setEnabled("demo@local-mp", true);
        assertThat(provider.list()).hasSize(2);
        plugins.uninstall("demo@local-mp");
        assertThat(plugins.list()).isEmpty();
        assertThat(pluginsRoot.resolve("cache/local-mp/demo")).doesNotExist();
        // marketplace 保留,可重装
        assertThat(plugins.install("demo", "local-mp").enabled()).isTrue();
    }

    @Test
    void invalidSourceRejected() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> marketplaces.add("not a source"))
                .hasMessageContaining("无法识别的来源");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> marketplaces.add("/nonexistent/path"))
                .hasMessageContaining("不存在");
    }
}
