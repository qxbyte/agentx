package com.agentx.tools.builtin.web;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/** HTML → 可读正文提取：保留标题/段落/代码块，剔除 script/nav/footer 等噪音。 */
class HtmlTextTest {

    private static final String PAGE = """
            <html><head><title>示例文档</title><script>alert(1)</script>
            <style>.x{color:red}</style></head>
            <body>
              <nav>首页 &gt; 文档 &gt; 教程</nav>
              <header>顶栏菜单</header>
              <main>
                <h1>快速开始</h1>
                <p>安装依赖后即可运行。</p>
                <pre><code>npm install
            npm run dev</code></pre>
                <ul><li>要点一</li><li>要点二</li></ul>
              </main>
              <footer>版权所有 © 2026</footer>
            </body></html>
            """;

    @Test
    void extractsMainContentAndDropsNoise() {
        String text = HtmlText.extract(PAGE, "https://example.com/doc");
        assertThat(text).contains("示例文档");      // 页面标题
        assertThat(text).contains("快速开始");      // h1
        assertThat(text).contains("安装依赖后即可运行");
        assertThat(text).contains("npm install");  // 代码块
        assertThat(text).contains("要点一");
        assertThat(text).doesNotContain("alert(1)");   // script 去除
        assertThat(text).doesNotContain("color:red");  // style 去除
        assertThat(text).doesNotContain("顶栏菜单");    // header 去除
        assertThat(text).doesNotContain("版权所有");    // footer 去除
    }

    @Test
    void fallsBackToBodyTextWhenNoBlockStructure() {
        // div 汤页面：无 main/article、无标准块级标签，回退 body.text() 不至于空手而归
        String divSoup = "<html><body><div><div>裸文本内容超过一点点长度以便断言</div></div></body></html>";
        String text = HtmlText.extract(divSoup, "https://example.com");
        assertThat(text).contains("裸文本内容");
    }

    @Test
    void truncatesOverlongContent() {
        String longPage = "<html><body><p>" + "字".repeat(20_000) + "</p></body></html>";
        String text = HtmlText.extract(longPage, "https://example.com");
        assertThat(text.length()).isLessThanOrEqualTo(8_200); // 8000 + 截断提示余量
        assertThat(text).contains("已截断");
    }
}
