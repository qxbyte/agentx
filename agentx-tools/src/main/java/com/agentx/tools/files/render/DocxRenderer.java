package com.agentx.tools.files.render;

import com.vladsch.flexmark.ast.BulletList;
import com.vladsch.flexmark.ast.Code;
import com.vladsch.flexmark.ast.Emphasis;
import com.vladsch.flexmark.ast.FencedCodeBlock;
import com.vladsch.flexmark.ast.Heading;
import com.vladsch.flexmark.ast.IndentedCodeBlock;
import com.vladsch.flexmark.ast.Link;
import com.vladsch.flexmark.ast.ListItem;
import com.vladsch.flexmark.ast.OrderedList;
import com.vladsch.flexmark.ast.Paragraph;
import com.vladsch.flexmark.ast.SoftLineBreak;
import com.vladsch.flexmark.ast.StrongEmphasis;
import com.vladsch.flexmark.ast.Text;
import com.vladsch.flexmark.ast.ThematicBreak;
import com.vladsch.flexmark.ext.tables.TableBlock;
import com.vladsch.flexmark.ext.tables.TableCell;
import com.vladsch.flexmark.ext.tables.TableRow;
import com.vladsch.flexmark.util.ast.Node;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Markdown → docx（POI XWPF）。品牌样式：黑白极简——标题层级加粗降阶、
 * 代码用等宽灰底、列表用符号缩进（不引入 numbering.xml，兼容性最好）。
 */
public final class DocxRenderer {

    private static final String BODY_FONT = "Microsoft YaHei";
    private static final String MONO_FONT = "Consolas";

    public byte[] render(String markdown) {
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (Node node : MarkdownSupport.parse(markdown).getChildren()) {
                renderBlock(doc, node);
            }
            doc.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void renderBlock(XWPFDocument doc, Node node) {
        switch (node) {
            case Heading h -> {
                XWPFParagraph p = doc.createParagraph();
                p.setSpacingBefore(h.getLevel() <= 2 ? 320 : 240);
                p.setSpacingAfter(120);
                renderInline(h, p, true, false, false, headingSize(h.getLevel()));
                if (h.getLevel() == 1) {
                    p.setBorderBottom(org.apache.poi.xwpf.usermodel.Borders.SINGLE);
                }
            }
            case Paragraph para -> {
                XWPFParagraph p = doc.createParagraph();
                p.setSpacingAfter(160);
                renderInline(para, p, false, false, false, 11);
            }
            case BulletList list -> renderList(doc, list, 0, false);
            case OrderedList list -> renderList(doc, list, 0, true);
            case FencedCodeBlock code -> renderCode(doc, code.getContentChars().toString());
            case IndentedCodeBlock code -> renderCode(doc, code.getContentChars().toString());
            case ThematicBreak ignored -> {
                XWPFParagraph p = doc.createParagraph();
                p.setBorderBottom(org.apache.poi.xwpf.usermodel.Borders.SINGLE);
            }
            case TableBlock table -> renderTable(doc, table);
            default -> {
                XWPFParagraph p = doc.createParagraph();
                renderInline(node, p, false, false, false, 11);
            }
        }
    }

    private void renderList(XWPFDocument doc, Node list, int level, boolean ordered) {
        int index = 1;
        for (Node item : list.getChildren()) {
            if (!(item instanceof ListItem)) {
                continue;
            }
            for (Node child : item.getChildren()) {
                if (child instanceof BulletList nested) {
                    renderList(doc, nested, level + 1, false);
                } else if (child instanceof OrderedList nested) {
                    renderList(doc, nested, level + 1, true);
                } else {
                    XWPFParagraph p = doc.createParagraph();
                    p.setIndentationLeft(360 + level * 360);
                    p.setSpacingAfter(80);
                    XWPFRun marker = p.createRun();
                    marker.setFontFamily(BODY_FONT);
                    marker.setFontSize(11);
                    marker.setText(ordered ? (index + ". ") : "• ");
                    renderInline(child, p, false, false, false, 11);
                }
            }
            index++;
        }
    }

    private void renderCode(XWPFDocument doc, String code) {
        for (String line : code.stripTrailing().split("\n", -1)) {
            XWPFParagraph p = doc.createParagraph();
            p.setIndentationLeft(240);
            p.setSpacingAfter(0);
            XWPFRun run = p.createRun();
            run.setFontFamily(MONO_FONT);
            run.setFontSize(9);
            run.setText(line);
        }
        doc.createParagraph().setSpacingAfter(120);
    }

    private void renderTable(XWPFDocument doc, TableBlock tableBlock) {
        java.util.List<java.util.List<String>> rows = new java.util.ArrayList<>();
        collectRows(tableBlock, rows);
        if (rows.isEmpty()) {
            return;
        }
        int cols = rows.getFirst().size();
        XWPFTable table = doc.createTable(rows.size(), cols);
        table.setWidth("100%");
        for (int r = 0; r < rows.size(); r++) {
            XWPFTableRow row = table.getRow(r);
            for (int c = 0; c < cols; c++) {
                XWPFTableCell cell = row.getCell(c);
                cell.removeParagraph(0);
                XWPFParagraph p = cell.addParagraph();
                p.setAlignment(ParagraphAlignment.LEFT);
                XWPFRun run = p.createRun();
                run.setFontFamily(BODY_FONT);
                run.setFontSize(10);
                run.setBold(r == 0);
                java.util.List<String> cells = rows.get(r);
                run.setText(c < cells.size() ? cells.get(c) : "");
            }
        }
        doc.createParagraph().setSpacingAfter(120);
    }

    private void collectRows(Node tableBlock, java.util.List<java.util.List<String>> rows) {
        for (Node section : tableBlock.getChildren()) {
            for (Node rowNode : section.getChildren()) {
                if (rowNode instanceof TableRow tr) {
                    java.util.List<String> cells = new java.util.ArrayList<>();
                    for (Node cell : tr.getChildren()) {
                        if (cell instanceof TableCell tc) {
                            cells.add(tc.getText().toString());
                        }
                    }
                    if (!cells.isEmpty()) {
                        rows.add(cells);
                    }
                }
            }
        }
    }

    /** 行内节点递归渲染：粗体/斜体/行内代码/链接（链接以「文字 (url)」降级呈现）。 */
    private void renderInline(Node parent, XWPFParagraph p, boolean bold, boolean italic,
                              boolean mono, int fontSize) {
        for (Node child : parent.getChildren()) {
            switch (child) {
                case Text text -> addRun(p, text.getChars().toString(), bold, italic, mono, fontSize);
                case StrongEmphasis strong -> renderInline(strong, p, true, italic, mono, fontSize);
                case Emphasis em -> renderInline(em, p, bold, true, mono, fontSize);
                case Code code -> addRun(p, code.getText().toString(), bold, italic, true, fontSize);
                case SoftLineBreak ignored -> addRun(p, " ", bold, italic, mono, fontSize);
                case Link link -> {
                    renderInline(link, p, bold, italic, mono, fontSize);
                    addRun(p, " (" + link.getUrl() + ")", bold, italic, true, 9);
                }
                default -> renderInline(child, p, bold, italic, mono, fontSize);
            }
        }
    }

    private void addRun(XWPFParagraph p, String text, boolean bold, boolean italic,
                        boolean mono, int fontSize) {
        if (text.isEmpty()) {
            return;
        }
        XWPFRun run = p.createRun();
        run.setFontFamily(mono ? MONO_FONT : BODY_FONT);
        run.setFontSize(mono && fontSize == 11 ? 10 : fontSize);
        run.setBold(bold);
        run.setItalic(italic);
        run.setText(text);
    }

    private int headingSize(int level) {
        return switch (level) {
            case 1 -> 20;
            case 2 -> 16;
            case 3 -> 13;
            default -> 12;
        };
    }
}
