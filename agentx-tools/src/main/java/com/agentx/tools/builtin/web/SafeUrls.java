package com.agentx.tools.builtin.web;

import java.net.IDN;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

/**
 * 网络工具的 SSRF 防护：仅放行指向公网的 http/https URL。
 * <p>
 * 模型给出的 URL 可能被 prompt 注入诱导指向内网（数据库控制台、云 metadata 端点等），
 * 抓取前必须校验目标解析后的全部地址；重定向的每一跳同样要过这里。
 * 已知残余风险：DNS rebinding 的 check-then-fetch 窗口（防御成本与收益不成比，接受）。
 */
public final class SafeUrls {

    private SafeUrls() {}

    /**
     * 校验 URL 合法且指向公网，返回规范化 URI；不合规抛 {@link IllegalArgumentException}。
     * 会触发一次 DNS 解析（字面量 IP 不触发）。
     */
    public static URI validate(String url) {
        return validate(url, true);
    }

    /**
     * 同 {@link #validate(String)}，但可关闭域名解析校验。
     * <p>
     * 经 HTTP 代理出网时（国内开发机常态），域名由代理端解析——本地解析要么不可用
     * （fake-IP DNS 只对代理链路生效），要么结果与代理端不一致，强行校验只会误伤。
     * 此时 {@code resolveDns=false}：语法/协议/字面量 IP 仍全量校验（http://127.0.0.1 依旧拒绝），
     * 仅跳过域名 → IP 的解析检查。
     */
    public static URI validate(String url, boolean resolveDns) {
        URI uri;
        try {
            uri = URI.create(url.trim());
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("URL 格式非法: " + url);
        }
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            throw new IllegalArgumentException("仅支持 http/https URL: " + url);
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("URL 缺少主机名: " + url);
        }
        if (!resolveDns && !isLiteralIp(host)) {
            return uri; // 代理模式：域名交给代理端解析，本地不查
        }
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(IDN.toASCII(host));
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("域名无法解析: " + host);
        }
        for (InetAddress address : addresses) {
            if (isBlockedAddress(address)) {
                throw new IllegalArgumentException("目标地址不允许访问（回环/内网/链路本地）: " + host);
            }
        }
        return uri;
    }

    /** 主机名是否为字面量 IP（IPv4 点分 / IPv6 含冒号，URI 里 IPv6 带方括号）。 */
    private static boolean isLiteralIp(String host) {
        String h = host.startsWith("[") && host.endsWith("]")
                ? host.substring(1, host.length() - 1) : host;
        return h.contains(":") || h.matches("\\d{1,3}(\\.\\d{1,3}){3}");
    }

    /** 是否为禁止访问的地址：回环 / any-local / 链路本地 / RFC1918 私网 / CGNAT / IPv6 ULA。 */
    public static boolean isBlockedAddress(InetAddress address) {
        if (address.isLoopbackAddress() || address.isAnyLocalAddress()
                || address.isLinkLocalAddress() || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }
        byte[] b = address.getAddress();
        if (b.length == 4) {
            int first = b[0] & 0xFF;
            int second = b[1] & 0xFF;
            // CGNAT 100.64.0.0/10（isSiteLocalAddress 不覆盖）
            return first == 100 && second >= 64 && second <= 127;
        }
        // IPv6 unique-local fc00::/7（isSiteLocalAddress 只认已废弃的 fec0::/10）
        int first = b[0] & 0xFF;
        return first == 0xFC || first == 0xFD;
    }
}
