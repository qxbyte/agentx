package com.agentx.coding.sandbox;

import com.agentx.common.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PathSandboxTest {

    @TempDir
    Path root;
    private PathSandbox sandbox;

    @BeforeEach
    void setUp() throws IOException {
        Files.createDirectories(root.resolve("src"));
        Files.writeString(root.resolve("src/App.java"), "class App {}");
        sandbox = PathSandbox.of(root.toString());
    }

    @Test
    void resolvesInsidePath() {
        Path resolved = sandbox.resolve("src/App.java");
        assertThat(resolved).exists();
        assertThat(sandbox.relativize(resolved)).isEqualTo("src/App.java");
    }

    @Test
    void allowsNonExistentTargetForWrite() {
        assertThat(sandbox.resolve("src/New.java")).doesNotExist();
    }

    @Test
    void rejectsDotDotEscape() {
        assertThatThrownBy(() -> sandbox.resolve("../../etc/passwd"))
                .isInstanceOf(BizException.class);
    }

    @Test
    void rejectsAbsoluteEscape() {
        // 绝对路径被当作相对根处理，跳到 /etc 会被前缀检查挡下
        assertThatThrownBy(() -> sandbox.resolve("/etc/../../etc/passwd"))
                .isInstanceOf(BizException.class);
    }

    @Test
    void treatsLeadingSlashAsWorkspaceRelative() {
        Path resolved = sandbox.resolve("/src/App.java");
        assertThat(resolved).exists();
    }

    /* ---------- 双根(只读第二根,如家目录) ---------- */

    @Test
    void dualRootAcceptsTildeAndAbsoluteInSecondary() throws IOException {
        Path home = Files.createTempDirectory("fake-home");
        Files.createDirectories(home.resolve("docs"));
        Files.writeString(home.resolve("docs/note.md"), "笔记");
        PathSandbox dual = PathSandbox.of(root.toString(), home.toString());

        // 相对路径仍解析到主根(工作区)
        assertThat(dual.resolve("src/App.java")).exists();
        // ~/ 指第二根
        assertThat(dual.resolve("~/docs/note.md")).exists();
        // 第二根内的绝对路径直接接受
        assertThat(dual.resolve(home.resolve("docs/note.md").toString())).exists();
        // 第二根外仍拒绝
        assertThatThrownBy(() -> dual.resolve("~/../outside.txt"))
                .isInstanceOf(BizException.class);
        // 展示路径:第二根内 → ~/ 前缀
        assertThat(dual.relativize(dual.resolve("~/docs/note.md"))).isEqualTo("~/docs/note.md");
    }

    @Test
    void singleRootTildeFallsBackToRoot() {
        // 无第二根时 ~ 退化为主根相对(本地工具场景根即家目录)
        assertThat(sandbox.resolve("~/src/App.java")).exists();
    }

    @Test
    void rejectsSymlinkEscape() throws IOException {
        Path outside = Files.createTempDirectory("outside-secret");
        Files.writeString(outside.resolve("secret.txt"), "top secret");
        try {
            Files.createSymbolicLink(root.resolve("leak"), outside);
        } catch (UnsupportedOperationException | IOException e) {
            return; // 平台不支持符号链接时跳过
        }
        assertThatThrownBy(() -> sandbox.resolve("leak/secret.txt"))
                .isInstanceOf(BizException.class);
    }
}
