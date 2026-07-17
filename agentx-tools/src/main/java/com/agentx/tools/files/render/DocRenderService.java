package com.agentx.tools.files.render;

import com.agentx.common.api.ErrorCode;
import com.agentx.common.exception.BizException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

/** 文档渲染分发：markdown 内容 → 目标格式字节流。 */
@Service
public class DocRenderService {

    public static final Set<String> DOCUMENT_FORMATS = Set.of("md", "docx", "pdf", "pptx");

    private final DocxRenderer docx = new DocxRenderer();
    private final PptxRenderer pptx = new PptxRenderer();
    private final PdfRenderer pdf;
    private final XlsxRenderer xlsx = new XlsxRenderer();

    public DocRenderService(@Value("${agentx.files.pdf-font:}") String pdfFont) {
        this.pdf = new PdfRenderer(pdfFont);
    }

    public byte[] renderDocument(String format, String markdown) {
        return switch (format) {
            case "md" -> (markdown == null ? "" : markdown).getBytes(StandardCharsets.UTF_8);
            case "docx" -> docx.render(markdown);
            case "pdf" -> pdf.render(markdown);
            case "pptx" -> pptx.render(markdown);
            default -> throw new BizException(ErrorCode.BAD_REQUEST, "不支持的文档格式: " + format);
        };
    }

    public byte[] renderSpreadsheet(List<XlsxRenderer.Sheet> sheets) {
        return xlsx.render(sheets == null ? List.of() : sheets);
    }
}
