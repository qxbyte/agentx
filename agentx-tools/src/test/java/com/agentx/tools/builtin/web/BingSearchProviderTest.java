package com.agentx.tools.builtin.web;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Bing RSS 结果解析（format=rss 服务端直出，绕开 HTML 端的 JS 反爬墙）：
 * item → title/link/description；零结果/结构异常时优雅返回空。
 */
class BingSearchProviderTest {

    private static final String RSS_PAGE = """
            <?xml version="1.0" encoding="utf-8"?>
            <rss version="2.0">
            <channel>
              <title>Bing: spring ai</title>
              <link>https://www.bing.com/search?q=spring+ai&amp;format=rss</link>
              <item>
                <title>Spring AI 官方文档</title>
                <link>https://spring.io/projects/spring-ai</link>
                <description>Spring AI 提供了统一的&lt;b&gt;模型抽象&lt;/b&gt;……</description>
              </item>
              <item>
                <title>Reference Doc</title>
                <link>https://docs.spring.io/spring-ai/reference/</link>
                <description>参考手册，包含 ChatClient 与 RAG 指南。</description>
              </item>
              <item>
                <title>GitHub 仓库</title>
                <link>https://github.com/spring-projects/spring-ai</link>
                <description>源码仓库。</description>
              </item>
            </channel>
            </rss>
            """;

    @Test
    void parsesRssItems() {
        List<SearchProvider.SearchResult> results = BingSearchProvider.parseResults(RSS_PAGE, 10);
        assertThat(results).hasSize(3);
        assertThat(results.get(0).title()).isEqualTo("Spring AI 官方文档");
        assertThat(results.get(0).url()).isEqualTo("https://spring.io/projects/spring-ai");
        // description 里的转义 HTML 标签应被剥掉，只留纯文本
        assertThat(results.get(0).snippet()).contains("模型抽象").doesNotContain("<b>");
        // channel 级 title 不应被误当结果
        assertThat(results).extracting(SearchProvider.SearchResult::title)
                .doesNotContain("Bing: spring ai");
    }

    @Test
    void respectsTopN() {
        assertThat(BingSearchProvider.parseResults(RSS_PAGE, 2)).hasSize(2);
    }

    @Test
    void returnsEmptyOnUnexpectedContent() {
        assertThat(BingSearchProvider.parseResults("<html><body>验证码页面</body></html>", 5)).isEmpty();
    }
}
