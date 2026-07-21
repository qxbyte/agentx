package com.agentx.tools.web;

import com.agentx.common.api.ApiResponse;
import com.agentx.common.api.ErrorCode;
import com.agentx.common.exception.BizException;
import com.agentx.tools.builtin.web.ProxySettings;
import com.agentx.tools.builtin.web.WebFetcher;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 网络工具代理配置端点（ADMIN）：设置页显式配置代理，保存即时生效（无需重启）。
 * 未配置/未启用时网络工具一律直连——{@link ProxySettings} 绝不回退系统/环境代理。
 */
@RestController
@RequiredArgsConstructor
public class ProxyController {

    private final ProxySettings proxySettings;
    private final WebFetcher webFetcher;

    public record ProxyView(boolean enabled, String host, Integer port) {
        static ProxyView of(ProxySettings.Config c) {
            return new ProxyView(c.enabled(), c.host(), c.port());
        }
    }

    public record UpdateRequest(boolean enabled, String host, Integer port) {}

    @GetMapping("/api/v1/admin/proxy")
    public ApiResponse<ProxyView> get() {
        return ApiResponse.ok(ProxyView.of(proxySettings.get()));
    }

    @PutMapping("/api/v1/admin/proxy")
    public ApiResponse<ProxyView> update(@RequestBody UpdateRequest req) {
        if (req.enabled()
                && (req.host() == null || req.host().isBlank() || req.port() == null || req.port() <= 0)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "启用代理时必须填写有效的主机与端口");
        }
        ProxySettings.Config saved = proxySettings.save(
                new ProxySettings.Config(req.enabled(), req.host(), req.port()));
        return ApiResponse.ok(ProxyView.of(saved));
    }

    /**
     * 测试当前代理配置：按已保存的配置抓取一个国外常用探测地址，
     * 直连不通、走代理才通——用于验证代理是否真正生效。
     */
    @PostMapping("/api/v1/admin/proxy/test")
    public ApiResponse<Map<String, Object>> test() {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            WebFetcher.FetchResult r = webFetcher.fetch("https://www.google.com/generate_204");
            result.put("ok", true);
            result.put("finalUrl", r.finalUrl());
        } catch (Exception e) {
            result.put("ok", false);
            result.put("error", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
        return ApiResponse.ok(result);
    }
}
