package com.agentx.tools.builtin.web;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.springframework.stereotype.Component;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Bing 搜索实现（默认 SearchProvider）：走 {@code format=rss} 输出——服务端直出 XML，
 * 免 key、免注册，且绕开 HTML 端对无 cookie 请求的 JS 反爬墙（实测 HTML 端只回跳转壳）。
 * <p>
 * 非官方承诺的接口——输出为空时记告警并返回空列表（工具层给模型友好提示），
 * 不抛错中断对话。需要稳定 SLA 时按 {@link SearchProvider} 接入博查/Tavily。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BingSearchProvider implements SearchProvider {

    private final WebFetcher fetcher;

    @Override
    public List<SearchResult> search(String query, int topN) throws Exception {
        String url = "https://www.bing.com/search?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8)
                + "&format=rss&count=" + topN + "&mkt=zh-CN";
        WebFetcher.FetchResult page = fetcher.fetch(url);
        List<SearchResult> results =
                parseResults(new String(page.body(), StandardCharsets.UTF_8), topN);
        if (results.isEmpty()) {
            log.warn("Bing RSS 结果为空（接口行为可能已变化）：query={}", query);
        }
        return results;
    }

    /** 解析 RSS：channel 下的 item → title/link/description（description 剥掉转义 HTML 留纯文本）。 */
    static List<SearchResult> parseResults(String xml, int topN) {
        Document doc = Jsoup.parse(xml, "", Parser.xmlParser());
        List<SearchResult> results = new ArrayList<>();
        for (Element item : doc.select("item")) {
            String title = textOf(item, "title");
            String url = textOf(item, "link");
            // description 内是转义过的 HTML（&lt;b&gt;…），再过一遍 HTML 解析取纯文本
            String snippet = Jsoup.parse(textOf(item, "description")).text();
            if (!title.isEmpty() && !url.isEmpty()) {
                results.add(new SearchResult(title, url, snippet));
            }
            if (results.size() >= topN) {
                break;
            }
        }
        return results;
    }

    private static String textOf(Element item, String tag) {
        Element el = item.selectFirst("> " + tag);
        return el == null ? "" : el.text().strip();
    }
}
