package com.agentx.tools.builtin.web;

import com.agentx.tools.registry.AgentTool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

/**
 * 网络工具（webFetch / webSearch）：
 * webFetch 抓取网页提取正文，webSearch 联网搜索返回结果列表。
 * 只读操作，不需要审批；SSRF 防护见 {@link SafeUrls}。
 */
@Slf4j
@AgentTool(group = "web")
@RequiredArgsConstructor
public class WebTools {

    private static final int DEFAULT_TOP_N = 5;
    private static final int MAX_TOP_N = 10;

    private final WebFetcher fetcher;
    private final SearchProvider searchProvider;

    @Tool(description = "抓取网页并提取正文内容。用于阅读在线文档、查资料、打开搜索结果或报错信息里的链接。"
            + "仅支持公网 http/https 地址")
    public String webFetch(
            @ToolParam(description = "完整 URL，如 https://example.com/doc") String url,
            @ToolParam(description = "用几个字说明这一步意图，如：查看项目结构", required = false) String purpose) {
        try {
            WebFetcher.FetchResult result = fetcher.fetch(url);
            String contentType = result.contentType().toLowerCase(Locale.ROOT);
            String raw = new String(result.body(), charsetOf(contentType));
            String text;
            if (contentType.contains("html") || contentType.isEmpty()) {
                text = HtmlText.extract(raw, result.finalUrl());
            } else {
                // JSON / 纯文本等：原样返回（同样受长度上限约束）
                text = raw.length() > HtmlText.MAX_CHARS
                        ? raw.substring(0, HtmlText.MAX_CHARS) + "\n\n[内容过长，已截断]"
                        : raw;
            }
            if (text.isBlank()) {
                return "页面抓取成功但未提取到可读正文（可能是纯 JS 渲染页面）：" + result.finalUrl();
            }
            return "来源: " + result.finalUrl() + "\n\n" + text;
        } catch (IllegalArgumentException e) {
            return "无法访问该 URL：" + e.getMessage();
        } catch (Exception e) {
            log.warn("webFetch 失败: url={}", url, e);
            return "网页抓取失败（" + e.getMessage() + "），可换个 URL 或稍后重试。";
        }
    }

    /** 从 Content-Type 提取 charset（如 text/html; charset=gbk），缺省/非法回退 UTF-8。 */
    private static java.nio.charset.Charset charsetOf(String contentType) {
        int idx = contentType.indexOf("charset=");
        if (idx < 0) {
            return StandardCharsets.UTF_8;
        }
        String name = contentType.substring(idx + 8).split("[;,\\s]")[0].replace("\"", "").strip();
        try {
            return java.nio.charset.Charset.forName(name);
        } catch (Exception e) {
            return StandardCharsets.UTF_8;
        }
    }

    @Tool(description = "联网搜索，返回标题/链接/摘要列表。需要阅读某条结果的详细内容时，"
            + "用 webFetch 打开对应链接")
    public String webSearch(
            @ToolParam(description = "搜索关键词") String query,
            @ToolParam(required = false, description = "结果条数，默认 5，最多 10") Integer topN,
            @ToolParam(description = "用几个字说明这一步意图，如：查看项目结构", required = false) String purpose) {
        int n = topN == null ? DEFAULT_TOP_N : Math.max(1, Math.min(topN, MAX_TOP_N));
        try {
            List<SearchProvider.SearchResult> results = searchProvider.search(query, n);
            if (results.isEmpty()) {
                return "未搜索到相关结果（或搜索源暂不可用），可调整关键词重试。";
            }
            StringBuilder sb = new StringBuilder("搜索结果（").append(results.size()).append(" 条）：\n");
            for (int i = 0; i < results.size(); i++) {
                SearchProvider.SearchResult r = results.get(i);
                sb.append('\n').append(i + 1).append(". ").append(r.title())
                        .append('\n').append("   ").append(r.url());
                if (!r.snippet().isEmpty()) {
                    sb.append('\n').append("   ").append(r.snippet());
                }
            }
            return sb.toString();
        } catch (Exception e) {
            log.warn("webSearch 失败: query={}", query, e);
            return "搜索失败（" + e.getMessage() + "），请稍后重试。";
        }
    }
}
