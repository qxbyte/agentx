package com.agentx.tools.builtin.web;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * 受控 HTTP 抓取器：SSRF 校验 + 手动跟随重定向（每跳复检）+ 响应体大小上限。
 * <p>
 * 不用自动重定向——公网 URL 可以 302 到内网地址绕过首跳校验，必须逐跳把关。
 * <p>
 * 代理支持：JDK HttpClient 默认无视 HTTPS_PROXY 等环境变量（curl 认、JVM 不认），
 * 国内代理环境（Clash/Surge fake-IP DNS）下本地解析常年不可用——按
 * Java 系统属性 → 通用环境变量的顺序探测代理并接入；代理模式下域名由代理端解析，
 * SSRF 的 DNS 校验相应关闭（字面量 IP 校验保留）。
 */
@Slf4j
@Component
public class WebFetcher {

    /** 响应体读取上限：2MB（正文提取只需要前面一小段，网页正常不超过） */
    static final int MAX_BODY_BYTES = 2 * 1024 * 1024;
    private static final int MAX_REDIRECTS = 3;
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    /** 常规桌面 UA：不少站点对无 UA/爬虫 UA 直接 403 */
    private static final String USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/126.0.0.0 Safari/537.36";

    private final HttpClient client;
    /** 是否经代理出网：代理模式下域名由代理端解析，SSRF 校验跳过本地 DNS 检查 */
    private final boolean viaProxy;

    public WebFetcher() {
        ProxySelector proxy = detectProxy();
        this.viaProxy = proxy != null;
        HttpClient.Builder builder = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(TIMEOUT);
        if (viaProxy) {
            builder.proxy(proxy);
        }
        this.client = builder.build();
    }

    /** Java 系统属性（https.proxyHost 等）优先，其次 HTTPS_PROXY/http_proxy 环境变量；都没有则直连。 */
    private static ProxySelector detectProxy() {
        String host = System.getProperty("https.proxyHost", System.getProperty("http.proxyHost"));
        String port = System.getProperty("https.proxyPort", System.getProperty("http.proxyPort"));
        if (host != null && !host.isBlank() && port != null) {
            log.info("网络工具经系统属性代理出网: {}:{}", host, port);
            return ProxySelector.of(new InetSocketAddress(host, Integer.parseInt(port)));
        }
        for (String key : new String[]{"HTTPS_PROXY", "https_proxy", "HTTP_PROXY", "http_proxy"}) {
            String value = System.getenv(key);
            if (value != null && !value.isBlank()) {
                try {
                    URI u = URI.create(value.trim());
                    int p = u.getPort() > 0 ? u.getPort() : 80;
                    log.info("网络工具经环境变量 {} 代理出网: {}:{}", key, u.getHost(), p);
                    return ProxySelector.of(new InetSocketAddress(u.getHost(), p));
                } catch (RuntimeException e) {
                    log.warn("代理环境变量格式非法，忽略: {}={}", key, value);
                }
            }
        }
        return null;
    }

    public record FetchResult(String finalUrl, String contentType, byte[] body, boolean truncated) {}

    /**
     * 抓取 URL（含 SSRF 校验与重定向逐跳复检）。
     *
     * @throws IllegalArgumentException URL 非法或指向内网
     * @throws IOException              网络失败 / 非 2xx / 重定向过深
     */
    public FetchResult fetch(String url) throws IOException, InterruptedException {
        URI target = SafeUrls.validate(url, !viaProxy);
        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            HttpRequest request = HttpRequest.newBuilder(target)
                    .timeout(TIMEOUT)
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "text/html,application/xhtml+xml,application/json;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                    .GET()
                    .build();
            HttpResponse<InputStream> response =
                    client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            int status = response.statusCode();

            if (status >= 300 && status < 400) {
                String location = response.headers().firstValue("Location")
                        .orElseThrow(() -> new IOException("重定向缺少 Location 头"));
                try (InputStream ignored = response.body()) { /* 丢弃重定向响应体 */ }
                // 逐跳复检：相对地址先解析为绝对，再过 SSRF 校验
                target = SafeUrls.validate(target.resolve(location).toString(), !viaProxy);
                continue;
            }
            if (status < 200 || status >= 300) {
                try (InputStream ignored = response.body()) { /* 丢弃错误响应体 */ }
                throw new IOException("HTTP " + status);
            }

            try (InputStream in = response.body()) {
                byte[] body = in.readNBytes(MAX_BODY_BYTES);
                boolean truncated = in.read() != -1; // 还有剩余 → 超上限被截断
                String contentType = response.headers().firstValue("Content-Type").orElse("");
                return new FetchResult(target.toString(), contentType, body, truncated);
            }
        }
        throw new IOException("重定向次数超过 " + MAX_REDIRECTS + " 次");
    }
}
