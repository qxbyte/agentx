package com.agentx.skill.service;

import com.agentx.skill.store.SkillFile;
import com.agentx.skill.store.SkillFileStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import static org.assertj.core.api.Assertions.assertThat;

/** 展开逻辑 + 文件存储（真实临时目录，不打桩）。 */
class SkillExpansionServiceTest {

    @TempDir
    Path root;

    private SkillFileStore store;
    private SkillExpansionService service;

    private SkillResourceService resources;

    @BeforeEach
    void setUp() {
        store = new SkillFileStore(root.toString());
        resources = new SkillResourceService(root.toString(), java.util.List.of(new StubProvider()));
        service = new SkillExpansionService(store, java.util.List.of(new StubProvider()), resources);
    }

    /** 模拟 plugin 命名空间来源:提供 demo:echo 一个 skill。 */
    static class StubProvider implements com.agentx.skill.store.SkillProvider {
        private final SkillFile skill = SkillFile.of("demo:echo", "插件示例", null,
                "插件指令：$ARGUMENTS", true, Instant.now());

        @Override
        public java.util.List<SkillFile> list() {
            return java.util.List.of(skill);
        }

        @Override
        public java.util.Optional<SkillFile> find(String name) {
            return "demo:echo".equals(name)
                    ? java.util.Optional.of(skill) : java.util.Optional.empty();
        }
    }

    private void givenSkill(String name, String content) {
        store.write(SkillFile.of(name, "测试", null, content, true, Instant.now()));
    }

    @Test
    void expandsArgumentsPlaceholder() {
        givenSkill("translate", "把以下内容翻译成英文：$ARGUMENTS");
        String result = service.expand("/translate 你好 世界");
        assertThat(result)
                .contains("<skill_instructions name=\"translate\" invoked_as=\"/translate 你好 世界\">")
                .contains("把以下内容翻译成英文：你好 世界")
                .endsWith("</skill_instructions>");
    }

    @Test
    void expandsPositionalPlaceholders() {
        givenSkill("fix-issue", "修复 issue $1，优先级 $2，缺失参数 $3。全部：$ARGUMENTS");
        String result = service.expand("/fix-issue 123 high");
        assertThat(result)
                .contains("修复 issue 123，优先级 high，缺失参数 。全部：123 high");
    }

    @Test
    void placeholderValueNotReExpanded() {
        // 参数值里的 $1 不应被二次替换（单次扫描）
        givenSkill("echo", "$ARGUMENTS 与 $1");
        String result = service.expand("/echo $1");
        assertThat(result).contains("$1 与 $1");
    }

    @Test
    void passesThroughWhenSkillNotFound() {
        assertThat(service.expand("/notexist foo")).isEqualTo("/notexist foo");
    }

    @Test
    void passesThroughNormalText() {
        assertThat(service.expand("普通消息 /not-a-command")).isEqualTo("普通消息 /not-a-command");
        assertThat(service.expand("1/2 是分数")).isEqualTo("1/2 是分数");
    }

    @Test
    void passesThroughWhenDisabled() {
        store.write(SkillFile.of("off", "停用", null, "body", false, Instant.now()));
        assertThat(service.expand("/off x")).isEqualTo("/off x");
    }

    @Test
    void escapesInvokedAsAttribute() {
        givenSkill("q", "$ARGUMENTS");
        String result = service.expand("/q <a href=\"x\">");
        assertThat(result).contains("invoked_as=\"/q &lt;a href=&quot;x&quot;&gt;\"");
    }

    /* ---------- provider（plugin 命名空间）组合 ---------- */

    @Test
    void expandsNamespacedSkillFromProvider() {
        String result = service.expand("/demo:echo 来自插件");
        assertThat(result)
                .contains("<skill_instructions name=\"demo:echo\"")
                .contains("插件指令：来自插件");
    }

    @Test
    void namespacedNotFoundPassesThrough() {
        assertThat(service.expand("/other:missing x")).isEqualTo("/other:missing x");
    }

    /* ---------- user-invocable / disable-model-invocation 双开关 ---------- */

    @Test
    void userNonInvocableSkillNeitherListedNorExpanded() throws IOException {
        // 标准 frontmatter:user-invocable: false → 不进 / 菜单、用户敲命令不展开(仅供 M2 模型触发)
        Files.writeString(root.resolve("intake.md"), """
                ---
                name: intake
                user-invocable: false
                description: 内部编排用,不面向用户
                ---

                内部指令：$ARGUMENTS""");
        assertThat(store.find("intake").orElseThrow().userInvocable()).isFalse();
        assertThat(service.expand("/intake 需求")).isEqualTo("/intake 需求");
        SkillService skillService = new SkillService(store, java.util.List.of());
        assertThat(skillService.listEnabled())
                .extracting(SkillFile::name).doesNotContain("intake");
    }

    @Test
    void disableModelInvocationParsedAndRoundTrips() throws IOException {
        Files.writeString(root.resolve("deploy.md"), """
                ---
                disable-model-invocation: true
                description: 有副作用,仅限用户显式触发
                ---

                执行发布""");
        SkillFile deploy = store.find("deploy").orElseThrow();
        assertThat(deploy.modelInvocable()).isFalse();
        assertThat(deploy.userInvocable()).isTrue();
        // 用户路径不受影响,仍可展开(无参数与带参数皆可)
        assertThat(service.expand("/deploy")).contains("执行发布");
        assertThat(service.expand("/deploy now")).contains("执行发布");
        // 回写保留标志
        store.write(deploy);
        assertThat(store.find("deploy").orElseThrow().modelInvocable()).isFalse();
    }

    /* ---------- L3 资源(references/ scripts/) ---------- */

    @Test
    void expansionAnnouncesResourceListForDirLayoutSkill() throws IOException {
        Files.createDirectories(root.resolve("guide/references"));
        Files.writeString(root.resolve("guide/SKILL.md"),
                "---\ndescription: 带资源的技能\n---\n\n按 references/patterns.md 的模式执行:$ARGUMENTS");
        Files.writeString(root.resolve("guide/references/patterns.md"), "# 模式甲\n先分析再动手");
        String result = service.expand("/guide 做个登录页");
        assertThat(result)
                .contains("readSkillFile")
                .contains("references/patterns.md");
        // 平铺技能无资源目录 → 不附清单
        givenSkill("plain", "无资源:$ARGUMENTS");
        assertThat(service.expand("/plain x")).doesNotContain("readSkillFile");
    }

    @Test
    void resourceReadIsSandboxed() throws IOException {
        Files.createDirectories(root.resolve("guide/references"));
        Files.writeString(root.resolve("guide/SKILL.md"), "body");
        Files.writeString(root.resolve("guide/references/a.md"), "资源内容");
        Files.writeString(root.resolve("secret.md"), "越界目标");
        assertThat(resources.read("guide", "references/a.md")).isEqualTo("资源内容");
        assertThat(resources.read("guide", "../secret.md")).contains("非法路径");
        assertThat(resources.read("guide", "references/none.md")).contains("文件不存在");
        assertThat(resources.listResources("guide")).containsExactly("references/a.md");
    }

    /* ---------- 文件存储行为（目录化配置的核心特性） ---------- */

    @Test
    void handWrittenFileWithoutFrontmatterWorks() throws IOException {
        // 手动扔一个纯 markdown 文件进目录 → 即时可用（Codex 式体验）
        Files.writeString(root.resolve("review.md"), "帮我审查以下代码：$ARGUMENTS");
        assertThat(service.expand("/review print(1)")).contains("帮我审查以下代码：print(1)");
        assertThat(store.scan()).extracting(SkillFile::name).contains("review");
    }

    @Test
    void dirLayoutTakesPrecedenceOverFlatFile() throws IOException {
        Files.writeString(root.resolve("dup.md"), "flat body");
        Files.createDirectories(root.resolve("dup"));
        Files.writeString(root.resolve("dup").resolve("SKILL.md"),
                "---\ndescription: 目录版\n---\n\ndir body");
        assertThat(store.find("dup").orElseThrow().content()).isEqualTo("dir body");
        assertThat(store.scan()).hasSize(1);
    }

    @Test
    void frontmatterRoundTrip() {
        store.write(SkillFile.of("meta", "描述文字", "[参数]", "body text", false, Instant.now()));
        SkillFile loaded = store.find("meta").orElseThrow();
        assertThat(loaded.description()).isEqualTo("描述文字");
        assertThat(loaded.argumentHint()).isEqualTo("[参数]");
        assertThat(loaded.enabled()).isFalse();
        assertThat(loaded.content()).isEqualTo("body text");
    }

    @Test
    void deleteRemovesBothLayouts() throws IOException {
        Files.writeString(root.resolve("gone.md"), "x");
        Files.createDirectories(root.resolve("gone"));
        Files.writeString(root.resolve("gone").resolve("SKILL.md"), "y");
        store.delete("gone");
        assertThat(store.exists("gone")).isFalse();
    }
}
