package com.agentx.tools.builtin.web;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * HTML → 可读正文：给模型"读网页"用的轻量提取器。
 * 去噪（script/nav/footer 等）→ 优先 main/article → 按块级元素拼接保留结构；
 * 块级结构过少的 div 汤页面回退 body.text()。输出截断到 {@value #MAX_CHARS} 字。
 */
final class HtmlText {

    /** 输出给模型的正文上限（字符）：控制上下文占用，超出截断并注明 */
    static final int MAX_CHARS = 8_000;

    private static final String NOISE_SELECTOR =
            "script,style,nav,footer,header,aside,form,iframe,noscript,svg,button";
    private static final String BLOCK_SELECTOR =
            "h1,h2,h3,h4,h5,h6,p,li,pre,blockquote,td,dt,dd,figcaption";

    private HtmlText() {}

    static String extract(String html, String baseUri) {
        Document doc = Jsoup.parse(html, baseUri);
        doc.select(NOISE_SELECTOR).remove();

        Element root = doc.selectFirst("main");
        if (root == null) root = doc.selectFirst("article");
        if (root == null) root = doc.body();
        if (root == null) return "";

        // 按块级元素拼接：保留标题/段落/代码块边界；LinkedHashSet 去掉嵌套块（如 li 里的 p）的重复文本
        Set<String> blocks = new LinkedHashSet<>();
        for (Element el : root.select(BLOCK_SELECTOR)) {
            String text = el.is("pre") ? el.wholeText().strip() : el.text().strip();
            if (!text.isEmpty()) {
                blocks.add(text);
            }
        }
        String body = String.join("\n\n", blocks);
        if (body.length() < 80) {
            // div 汤页面：无标准块级结构，回退全文文本
            body = root.text();
        }

        String title = doc.title().strip();
        String result = title.isEmpty() ? body : title + "\n\n" + body;
        if (result.length() > MAX_CHARS) {
            result = result.substring(0, MAX_CHARS) + "\n\n[内容过长，已截断]";
        }
        return result;
    }
}
