package com.agentx.tools.files.render;

import com.vladsch.flexmark.ext.tables.TablesExtension;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Document;
import com.vladsch.flexmark.util.data.MutableDataSet;
import java.util.List;

/** flexmark 解析/渲染的共享配置（启用 GFM 表格）。 */
final class MarkdownSupport {
    private static final MutableDataSet OPTIONS = new MutableDataSet()
            .set(Parser.EXTENSIONS, List.of(TablesExtension.create()));

    static final Parser PARSER = Parser.builder(OPTIONS).build();
    static final HtmlRenderer HTML = HtmlRenderer.builder(OPTIONS).build();

    static Document parse(String markdown) {
        return PARSER.parse(markdown == null ? "" : markdown);
    }

    private MarkdownSupport() {}
}
