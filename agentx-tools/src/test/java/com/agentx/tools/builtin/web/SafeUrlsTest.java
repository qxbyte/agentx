package com.agentx.tools.builtin.web;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.net.InetAddress;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** SSRF 防护：仅放行公网 http/https，回环/私网/链路本地/CGNAT/ULA 一律拒绝。 */
class SafeUrlsTest {

    /* ---------- 协议 ---------- */

    @ParameterizedTest
    @ValueSource(strings = {"ftp://example.com/x", "file:///etc/passwd", "gopher://x", "javascript:alert(1)"})
    void rejectsNonHttpSchemes(String url) {
        assertThatThrownBy(() -> SafeUrls.validate(url)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMalformedUrl() {
        assertThatThrownBy(() -> SafeUrls.validate("not a url")).isInstanceOf(IllegalArgumentException.class);
    }

    /* ---------- 地址段（纯 IP 判定，不发真实 DNS/请求） ---------- */

    @ParameterizedTest
    @ValueSource(strings = {
            "127.0.0.1",      // 回环
            "10.1.2.3",       // RFC1918
            "172.16.0.1",     // RFC1918
            "172.31.255.255", // RFC1918 上界
            "192.168.1.1",    // RFC1918
            "169.254.169.254",// 链路本地（云 metadata）
            "100.64.0.1",     // CGNAT 100.64/10
            "0.0.0.0",        // any-local
    })
    void rejectsPrivateIpv4(String ip) throws Exception {
        assertThat(SafeUrls.isBlockedAddress(InetAddress.getByName(ip))).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"::1", "fe80::1", "fc00::1", "fd12:3456::1"})
    void rejectsLoopbackAndPrivateIpv6(String ip) throws Exception {
        assertThat(SafeUrls.isBlockedAddress(InetAddress.getByName(ip))).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"1.1.1.1", "8.8.8.8", "104.16.132.229", "2606:4700::6810:84e5"})
    void allowsPublicAddresses(String ip) throws Exception {
        assertThat(SafeUrls.isBlockedAddress(InetAddress.getByName(ip))).isFalse();
    }

    /* ---------- 整链校验（字面量 IP 主机名不触发 DNS） ---------- */

    @ParameterizedTest
    @ValueSource(strings = {
            "http://127.0.0.1:8080/admin",
            "http://192.168.0.10/",
            "http://169.254.169.254/latest/meta-data/",
            "http://[::1]/",
            "http://172.20.3.4:9200/_cat",
    })
    void validateRejectsPrivateLiteralHosts(String url) {
        assertThatThrownBy(() -> SafeUrls.validate(url)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validateAllowsPublicLiteralHost() {
        // 公网字面量 IP：应通过（不发请求，仅校验）
        SafeUrls.validate("https://1.1.1.1/dns-query");
    }

    /* ---------- 代理模式（resolveDns=false）：跳过域名解析，字面量 IP 仍全量拦截 ---------- */

    @Test
    void proxyModeSkipsDnsButStillBlocksLiteralPrivateIps() {
        // 域名不触发本地解析（本地 DNS 在 fake-IP 代理环境下不可用）
        SafeUrls.validate("https://this-domain-should-not-resolve-locally.example", false);
        // 字面量内网 IP 依旧必须拒绝
        assertThatThrownBy(() -> SafeUrls.validate("http://127.0.0.1:8080/admin", false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SafeUrls.validate("http://169.254.169.254/latest/meta-data/", false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SafeUrls.validate("http://[::1]/", false))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
