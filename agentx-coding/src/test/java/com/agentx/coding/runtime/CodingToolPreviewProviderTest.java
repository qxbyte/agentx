package com.agentx.coding.runtime;

import com.agentx.coding.tools.WorkspaceContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.chat.model.ToolContext;
import tools.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

/** writeFile 的 diff 预览：修改生成红绿行、新文件全绿、超限/无上下文回退基础 preview。 */
class CodingToolPreviewProviderTest {

    private final ObjectMapper om = new ObjectMapper();
    private final CodingToolPreviewProvider provider = new CodingToolPreviewProvider(om);

    private ToolContext ctx(Path root) {
        return new ToolContext(Map.of(WorkspaceContext.WORKSPACE_ROOT, root.toString()));
    }

    private String args(String path, String content) {
        return "{\"path\":\"" + path + "\",\"content\":\"" + content + "\"}";
    }

    @Test
    void modifiedFileYieldsUnifiedDiff(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve("a.txt"), "line1\nline2\n");
        Map<String, Object> preview = provider.previewOf("writeFile",
                args("a.txt", "line1\\nlineX\\n"), ctx(root));
        assertThat(preview).containsEntry("path", "a.txt").containsKey("diff")
                .doesNotContainKey("content");
        String diff = String.valueOf(preview.get("diff"));
        assertThat(diff).contains("--- a/a.txt").contains("+++ b/a.txt")
                .contains("-line2").contains("+lineX");
    }

    @Test
    void newFileYieldsAllAdditions(@TempDir Path root) {
        Map<String, Object> preview = provider.previewOf("writeFile",
                args("new.txt", "hello\\n"), ctx(root));
        assertThat(String.valueOf(preview.get("diff"))).contains("+hello");
    }

    @Test
    void oversizeOrMissingContextFallsBack(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve("big.txt"), "x".repeat(100_001));
        Map<String, Object> oversize = provider.previewOf("writeFile",
                args("big.txt", "y"), ctx(root));
        assertThat(oversize).containsKey("content").doesNotContainKey("diff");

        Map<String, Object> noCtx = provider.previewOf("writeFile", args("a.txt", "y"), null);
        assertThat(noCtx).containsKey("content").doesNotContainKey("diff");
    }

    @Test
    void nonWriteToolDelegatesToBasePreview(@TempDir Path root) {
        Map<String, Object> preview = provider.previewOf("runShell",
                "{\"command\":\"ls\"}", ctx(root));
        assertThat(preview).containsEntry("command", "ls");
    }
}
