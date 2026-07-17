package com.agentx.tools.files.render;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 渲染引擎冒烟：四种二进制格式真实生成非空产物且可被对应解析器打开。 */
class RenderSmokeTest {

    private static final String MD = """
            # AgentX 项目介绍

            企业级智能体平台，支持多轮对话、知识库检索与工具调用。

            ## 核心能力

            - 多轮对话与**深度思考**
            - RAG 知识库：`向量 + BM25` 混合检索
              - RRF 融合重排
            - 工具调用与计划拆解

            ## 数据一览

            | 模块 | 语言 | 说明 |
            | ---- | ---- | ---- |
            | chat | Java | 流式对话 |
            | web  | TS   | 前端 |

            ```java
            var x = 1;
            ```
            """;

    @Test
    void docx() throws Exception {
        byte[] bytes = new DocxRenderer().render(MD);
        assertTrue(bytes.length > 1000);
        try (var doc = new org.apache.poi.xwpf.usermodel.XWPFDocument(
                new java.io.ByteArrayInputStream(bytes))) {
            assertTrue(doc.getParagraphs().size() > 3);
        }
        Files.write(Path.of("/tmp/agentx-smoke.docx"), bytes);
    }

    @Test
    void pptx() throws Exception {
        byte[] bytes = new PptxRenderer().render(MD);
        assertTrue(bytes.length > 1000);
        try (var ppt = new org.apache.poi.xslf.usermodel.XMLSlideShow(
                new java.io.ByteArrayInputStream(bytes))) {
            assertTrue(ppt.getSlides().size() >= 3, "封面 + 两个内容页");
        }
        Files.write(Path.of("/tmp/agentx-smoke.pptx"), bytes);
    }

    @Test
    void pdf() throws Exception {
        byte[] bytes = new PdfRenderer("").render(MD);
        assertTrue(bytes.length > 1000);
        assertTrue(new String(bytes, 0, 5).startsWith("%PDF"));
        Files.write(Path.of("/tmp/agentx-smoke.pdf"), bytes);
    }

    @Test
    void xlsx() throws Exception {
        byte[] bytes = new XlsxRenderer().render(List.of(new XlsxRenderer.Sheet(
                "统计", List.of("模块", "行数"), List.of(List.of("chat", "1200"), List.of("web", "3400")))));
        assertTrue(bytes.length > 1000);
        try (var wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook(
                new java.io.ByteArrayInputStream(bytes))) {
            assertTrue(wb.getSheet("统计").getRow(1).getCell(1).getNumericCellValue() == 1200);
        }
        Files.write(Path.of("/tmp/agentx-smoke.xlsx"), bytes);
    }
}
