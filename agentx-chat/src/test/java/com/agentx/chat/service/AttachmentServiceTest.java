package com.agentx.chat.service;

import com.agentx.chat.domain.ChatAttachment;
import com.agentx.chat.domain.ChatAttachmentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.springframework.mock.web.MockMultipartFile;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AttachmentServiceTest {

    @TempDir
    Path tempDir;

    private AttachmentService service() {
        ChatAttachmentRepository repo = Mockito.mock(ChatAttachmentRepository.class);
        Mockito.when(repo.save(Mockito.any())).thenAnswer(inv -> inv.getArgument(0));
        return new AttachmentService(repo, tempDir.toString());
    }

    @Test
    void uploadParsesTextAndMarkdown() {
        var results = service().upload(UUID.randomUUID(), List.of(
                new MockMultipartFile("files", "笔记.md", "text/markdown",
                        "# 标题\n正文内容".getBytes()),
                new MockMultipartFile("files", "data.json", "application/json",
                        "{\"a\":1}".getBytes())), List.of("", "src/data.json"));
        assertEquals(2, results.size());
        assertNull(results.get(0).error());
        assertNotNull(results.get(0).id());
        assertTrue(results.get(0).charCount() > 0);
        assertEquals("src/data.json", results.get(1).relPath());
    }

    @Test
    void rejectsUnsupportedExtension() {
        var results = service().upload(UUID.randomUUID(), List.of(
                new MockMultipartFile("files", "app.exe", null, new byte[10])), null);
        assertNotNull(results.get(0).error());
        assertNull(results.get(0).id());
    }

    @Test
    void wrapForPromptUsesDocumentsXml() {
        ChatAttachment a = new ChatAttachment();
        a.setFilename("说明.md");
        a.setParsedText("文档正文");
        a.setCharCount(4);
        String wrapped = service().wrapForPrompt(List.of(a), "总结这份文档");
        assertTrue(wrapped.startsWith("<documents>"));
        assertTrue(wrapped.contains("<source>说明.md</source>"));
        assertTrue(wrapped.contains("文档正文"));
        assertTrue(wrapped.endsWith("总结这份文档"));
    }

    @Test
    void truncatedFileCarriesExplicitNote() {
        ChatAttachment a = new ChatAttachment();
        a.setFilename("大文件.txt");
        a.setParsedText("头部内容");
        a.setCharCount(40_000);
        a.setTruncated(true);
        String wrapped = service().wrapForPrompt(List.of(a), "问题");
        assertTrue(wrapped.contains("已按单文件上限截断"));
    }
}
