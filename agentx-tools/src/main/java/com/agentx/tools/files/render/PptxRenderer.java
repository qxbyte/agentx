package com.agentx.tools.files.render;

import org.apache.poi.sl.usermodel.TextParagraph;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFAutoShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextBox;
import org.apache.poi.xslf.usermodel.XSLFTextParagraph;
import org.apache.poi.xslf.usermodel.XSLFTextRun;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.geom.Rectangle2D;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Markdown → pptx（POI XSLF）。按标题分页：`#` 为封面页（大标题+副标题），
 * 每个 `##` 一页内容页（页标题 + 列表/段落）。品牌版式：白底黑字极简，
 * 封面黑色标题块 + 细分隔线，内容页顶部细黑线 + 页脚页码。
 */
public final class PptxRenderer {

    private static final int W = 1280;
    private static final int H = 720;
    private static final Color INK = new Color(0x0d, 0x0d, 0x0d);
    private static final Color DIM = new Color(0x6e, 0x6e, 0x80);
    private static final String FONT = "Microsoft YaHei";

    private record SlideSpec(String title, List<String> lines, boolean cover) {}

    public byte[] render(String markdown) {
        List<SlideSpec> specs = split(markdown);
        try (XMLSlideShow ppt = new XMLSlideShow(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ppt.setPageSize(new Dimension(W, H));
            int page = 0;
            for (SlideSpec spec : specs) {
                page++;
                if (spec.cover()) {
                    renderCover(ppt.createSlide(), spec);
                } else {
                    renderContent(ppt.createSlide(), spec, page, specs.size());
                }
            }
            if (specs.isEmpty()) {
                renderCover(ppt.createSlide(), new SlideSpec("空文档", List.of(), true));
            }
            ppt.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** `#` 开启封面页，`##` 开启内容页；无任何标题时全部内容归入单页。 */
    private List<SlideSpec> split(String markdown) {
        List<SlideSpec> specs = new ArrayList<>();
        String title = null;
        boolean cover = false;
        List<String> lines = new ArrayList<>();
        for (String raw : (markdown == null ? "" : markdown).split("\n", -1)) {
            String line = raw.stripTrailing();
            if (line.startsWith("## ")) {
                flush(specs, title, lines, cover);
                title = line.substring(3).strip();
                cover = false;
                lines = new ArrayList<>();
            } else if (line.startsWith("# ")) {
                flush(specs, title, lines, cover);
                title = line.substring(2).strip();
                cover = true;
                lines = new ArrayList<>();
            } else if (!line.isBlank()) {
                lines.add(line);
            }
        }
        flush(specs, title, lines, cover);
        return specs;
    }

    private void flush(List<SlideSpec> specs, String title, List<String> lines, boolean cover) {
        if (title == null && lines.isEmpty()) {
            return;
        }
        specs.add(new SlideSpec(title == null ? "" : title, lines, cover));
    }

    private void renderCover(XSLFSlide slide, SlideSpec spec) {
        rule(slide, 96, 300, 72, 4);
        XSLFTextBox titleBox = textBox(slide, 96, 320, W - 192, 140);
        XSLFTextRun run = addLine(titleBox, spec.title(), 44.0, true, INK);
        run.setFontFamily(FONT);
        if (!spec.lines().isEmpty()) {
            XSLFTextBox sub = textBox(slide, 96, 460, W - 192, 120);
            for (String line : spec.lines()) {
                addLine(sub, plainText(line), 18.0, false, DIM);
            }
        }
        footer(slide, "AgentX", null);
    }

    private void renderContent(XSLFSlide slide, SlideSpec spec, int page, int total) {
        rule(slide, 72, 56, W - 144, 1.2);
        XSLFTextBox titleBox = textBox(slide, 72, 72, W - 144, 70);
        addLine(titleBox, spec.title(), 30.0, true, INK);

        XSLFTextBox body = textBox(slide, 72, 170, W - 144, H - 260);
        for (String line : spec.lines()) {
            int indent = bulletIndent(line);
            String text = plainText(line);
            if (line.startsWith("### ")) {
                addLine(body, line.substring(4).strip(), 20.0, true, INK);
            } else if (indent >= 0) {
                XSLFTextParagraph p = body.addNewTextParagraph();
                p.setIndentLevel(indent);
                p.setBullet(true);
                p.setLineSpacing(115.0);
                p.setSpaceAfter(30.0);
                XSLFTextRun run = p.addNewTextRun();
                run.setText(text);
                run.setFontFamily(FONT);
                run.setFontSize(indent == 0 ? 18.0 : 15.0);
                run.setFontColor(indent == 0 ? INK : DIM);
            } else {
                addLine(body, text, 16.0, false, INK);
            }
        }
        footer(slide, "AgentX", page + " / " + total);
    }

    /** 列表行的缩进层级：非列表行返回 -1。 */
    private int bulletIndent(String line) {
        String stripped = line.stripLeading();
        if (!stripped.startsWith("- ") && !stripped.startsWith("* ")) {
            return -1;
        }
        int spaces = line.length() - stripped.length();
        return Math.min(spaces / 2, 2);
    }

    /** 去掉行内 markdown 记号（bullet 前缀 / 粗体星号 / 行内代码反引号）。 */
    private String plainText(String line) {
        String s = line.stripLeading();
        if (s.startsWith("- ") || s.startsWith("* ")) {
            s = s.substring(2);
        }
        return s.replace("**", "").replace("`", "").strip();
    }

    private XSLFTextBox textBox(XSLFSlide slide, double x, double y, double w, double h) {
        XSLFTextBox box = slide.createTextBox();
        box.setAnchor(new Rectangle2D.Double(x, y, w, h));
        return box;
    }

    private XSLFTextRun addLine(XSLFTextBox box, String text, Double size, boolean bold, Color color) {
        XSLFTextParagraph p = box.addNewTextParagraph();
        p.setTextAlign(TextParagraph.TextAlign.LEFT);
        p.setSpaceAfter(20.0);
        XSLFTextRun run = p.addNewTextRun();
        run.setText(text);
        run.setFontFamily(FONT);
        run.setFontSize(size);
        run.setBold(bold);
        run.setFontColor(color);
        return run;
    }

    private void rule(XSLFSlide slide, double x, double y, double w, double thickness) {
        XSLFAutoShape bar = slide.createAutoShape();
        bar.setShapeType(org.apache.poi.sl.usermodel.ShapeType.RECT);
        bar.setAnchor(new Rectangle2D.Double(x, y, w, thickness));
        bar.setFillColor(INK);
        bar.setLineColor(INK);
    }

    private void footer(XSLFSlide slide, String brand, String pageLabel) {
        XSLFTextBox left = textBox(slide, 72, H - 52, 200, 28);
        addLine(left, brand, 11.0, true, DIM);
        if (pageLabel != null) {
            XSLFTextBox right = textBox(slide, W - 200, H - 52, 128, 28);
            XSLFTextParagraph p = right.getTextParagraphs().getFirst();
            p.setTextAlign(TextParagraph.TextAlign.RIGHT);
            XSLFTextRun run = p.addNewTextRun();
            run.setText(pageLabel);
            run.setFontFamily(FONT);
            run.setFontSize(11.0);
            run.setFontColor(DIM);
        }
    }
}
