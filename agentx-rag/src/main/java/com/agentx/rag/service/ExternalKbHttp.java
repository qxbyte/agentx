package com.agentx.rag.service;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import java.time.Duration;

/**
 * 外部知识库 HTTP 客户端工厂（探测与检索共用）。
 * 用 SimpleClientHttpRequestFactory（纯 HTTP/1.1）：JDK HttpClient 默认会发
 * h2c Upgrade 头，Fastify 等服务端会以 400 "Invalid Upgrade header" 拒绝。
 * 短超时保证外部库故障不拖慢对话链路（fail-open 由调用方兜底）。
 */
public final class ExternalKbHttp {

    private ExternalKbHttp() {}

    public static RestClient client(String baseUrl) {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(Duration.ofSeconds(3));
        f.setReadTimeout(Duration.ofSeconds(6));
        return RestClient.builder().baseUrl(baseUrl).requestFactory(f).build();
    }
}
