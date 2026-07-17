package com.agentx.tools.files.render;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.helper.W3CDom;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Markdown → HTML（flexmark）→ PDF（openhtmltopdf）。
 * 中文字体解析链：agentx.files.pdf-font 显式配置 → 常见系统 CJK 字体（TTF/OTF）；
 * 均未命中时仍可出 PDF，但中文会缺字形（启动日志有告警）。
 */
@Slf4j
public final class PdfRenderer {

    /** openhtmltopdf 仅支持 TTF/OTF 单字体文件（TTC 集合不支持），候选按平台罗列。 */
    private static final List<String> FONT_CANDIDATES = List.of(
            "/System/Library/Fonts/Supplemental/Arial Unicode.ttf",
            "/usr/share/fonts/opentype/noto/NotoSansCJK-SC-Regular.otf",
            "/usr/share/fonts/opentype/noto/NotoSansSC-Regular.otf");

    private static final String CSS = """
            body { font-family: 'CJK', sans-serif; font-size: 11pt; line-height: 1.7;
                   color: #111; margin: 48px 56px; }
            h1 { font-size: 22pt; border-bottom: 1.5pt solid #111; padding-bottom: 6pt; }
            h2 { font-size: 16pt; margin-top: 18pt; }
            h3 { font-size: 13pt; }
            code { font-family: 'CJK', monospace; font-size: 9.5pt;
                   background: #f2f2f2; padding: 1pt 3pt; }
            pre { background: #f5f5f5; border: 0.5pt solid #ddd; padding: 8pt;
                  font-size: 9pt; white-space: pre-wrap; word-wrap: break-word; }
            pre code { background: none; padding: 0; }
            table { border-collapse: collapse; width: 100%; margin: 8pt 0; }
            th, td { border: 0.5pt solid #999; padding: 4pt 8pt; font-size: 10pt; }
            th { background: #111; color: #fff; }
            blockquote { border-left: 2pt solid #ccc; margin-left: 0; padding-left: 10pt; color: #555; }
            a { color: #111; }
            hr { border: none; border-top: 0.75pt solid #999; }
            """;

    private final Path fontPath;

    public PdfRenderer(String configuredFont) {
        this.fontPath = resolveFont(configuredFont);
        if (fontPath == null) {
            log.warn("未找到可用的 CJK 字体（可配置 agentx.files.pdf-font），PDF 中文将缺字形");
        }
    }

    public byte[] render(String markdown) {
        String bodyHtml = MarkdownSupport.HTML.render(MarkdownSupport.parse(markdown));
        String html = "<html><head><style>" + CSS + "</style></head><body>" + bodyHtml + "</body></html>";
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            // jsoup 清洗为 W3C DOM：flexmark 输出的 HTML 不保证 XHTML 良构
            builder.withW3cDocument(new W3CDom().fromJsoup(Jsoup.parse(html)), null);
            if (fontPath != null) {
                builder.useFont(fontPath.toFile(), "CJK");
            }
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("PDF 渲染失败: " + e.getMessage(), e);
        }
    }

    private static Path resolveFont(String configured) {
        if (configured != null && !configured.isBlank() && Files.exists(Path.of(configured))) {
            return Path.of(configured);
        }
        for (String candidate : FONT_CANDIDATES) {
            if (new File(candidate).exists()) {
                return Path.of(candidate);
            }
        }
        return null;
    }
}
