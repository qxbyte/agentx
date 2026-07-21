package com.agentx.tools.builtin.web;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.io.InputStream;
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
 * 代理策略：<b>只认设置页显式配置的代理</b>（{@link ProxySettings}），
 * 绝不读取系统/环境代理——避免系统代理关闭时联网失败。未配置/未启用时一律直连。
 * 配置变更即时生效：每次抓取按 {@link ProxySettings#signature()} 决定是否重建 HttpClient，
 * 无需重启。代理模式下域名由代理端解析，SSRF 的本地 DNS 校验相应关闭（字面量 IP 校验保留）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebFetcher {

    /** 响应体读取上限：2MB（正文提取只需要前面一小段，网页正常不超过） */
    static final int MAX_BODY_BYTES = 2 * 1024 * 1024;
    private static final int MAX_REDIRECTS = 3;
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    /** 常规桌面 UA：不少站点对无 UA/爬虫 UA 直接 403 */
    private static final String USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/126.0.0.0 Safari/537.36";

    private final ProxySettings proxySettings;

    /** 按代理签名缓存的 HttpClient；配置变更（含开关切换）时惰性重建。 */
    private volatile HttpClient cachedClient;
    private volatile String cachedSignature;

    /** 取当前代理配置对应的 HttpClient：签名不变复用，变更时重建（保存代理配置后下次请求即生效）。 */
    private HttpClient client() {
        String sig = proxySettings.signature();
        HttpClient c = cachedClient;
        if (c != null && sig.equals(cachedSignature)) {
            return c;
        }
        synchronized (this) {
            if (cachedClient != null && sig.equals(cachedSignature)) {
                return cachedClient;
            }
            HttpClient.Builder builder = HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.NEVER)
                    .connectTimeout(TIMEOUT);
            ProxySelector proxy = proxySettings.selector();
            if (proxy != null) {
                builder.proxy(proxy);
                log.info("网络工具经配置代理出网: {}", sig);
            } else {
                log.info("网络工具直连出网（未配置/未启用代理）");
            }
            cachedClient = builder.build();
            cachedSignature = sig;
            return cachedClient;
        }
    }

    public record FetchResult(String finalUrl, String contentType, byte[] body, boolean truncated) {}

    /**
     * 抓取 URL（含 SSRF 校验与重定向逐跳复检）。
     *
     * @throws IllegalArgumentException URL 非法或指向内网
     * @throws IOException              网络失败 / 非 2xx / 重定向过深
     */
    public FetchResult fetch(String url) throws IOException, InterruptedException {
        // 逐次读取代理状态：代理开启时 SSRF 的本地 DNS 校验放宽（域名由代理端解析）
        boolean viaProxy = proxySettings.selector() != null;
        HttpClient client = client();
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
