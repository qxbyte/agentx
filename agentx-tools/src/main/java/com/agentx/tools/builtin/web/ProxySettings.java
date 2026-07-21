package com.agentx.tools.builtin.web;

import com.agentx.common.api.ErrorCode;
import com.agentx.common.exception.BizException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 网络工具（webFetch/webSearch）代理配置：仅由设置页显式配置，
 * <b>绝不读取系统/环境代理</b>——未配置或未启用时一律直连，避免系统代理关闭时联网失败。
 * <p>
 * 配置持久化到 {@code ~/.agentx/proxy.json}（本地 app 的机器级配置，不入库）；
 * 内存态用 AtomicReference 持有，保存即时生效——{@link WebFetcher} 每次请求读最新签名，
 * 变更时重建 HttpClient，无需重启服务。
 */
@Slf4j
@Component
public class ProxySettings {

    /** 代理配置：enabled + host + port。port 允许为空（未配置时）。 */
    public record Config(boolean enabled, String host, Integer port) {
        public static Config disabled() {
            return new Config(false, "", null);
        }

        /** 启用且主机端口完整才算可用，否则视为直连。 */
        public boolean usable() {
            return enabled && host != null && !host.isBlank() && port != null && port > 0;
        }
    }

    private final Path file;
    private final ObjectMapper mapper;
    private final AtomicReference<Config> current = new AtomicReference<>(Config.disabled());

    public ProxySettings(
            @Value("${agentx.proxy.config-file:${user.home}/.agentx/proxy.json}") String path,
            ObjectMapper mapper) {
        this.file = Path.of(path);
        this.mapper = mapper;
    }

    @PostConstruct
    void load() {
        if (!Files.exists(file)) {
            return;
        }
        try {
            Config loaded = mapper.readValue(Files.readString(file), Config.class);
            current.set(loaded);
            log.info("网络代理配置已加载: enabled={} {}:{}", loaded.enabled(), loaded.host(), loaded.port());
        } catch (Exception e) {
            log.warn("网络代理配置读取失败，按未启用处理: {}", e.getMessage());
        }
    }

    public Config get() {
        return current.get();
    }

    /** 保存配置：写入内存与本地文件；即时生效（WebFetcher 下一次请求即按新配置）。 */
    public synchronized Config save(Config cfg) {
        Config normalized = new Config(
                cfg.enabled(),
                cfg.host() == null ? "" : cfg.host().trim(),
                cfg.port());
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, mapper.writeValueAsString(normalized));
        } catch (Exception e) {
            log.error("网络代理配置写入失败: {}", e.getMessage());
            throw new BizException(ErrorCode.INTERNAL_ERROR, "代理配置保存失败: " + e.getMessage());
        }
        current.set(normalized);
        log.info("网络代理配置已更新: enabled={} {}:{}", normalized.enabled(), normalized.host(), normalized.port());
        return normalized;
    }

    /** 启用且配置完整时返回 HTTP 代理选择器，否则 null（直连）。绝不回退系统/环境代理。 */
    public ProxySelector selector() {
        Config c = current.get();
        return c.usable() ? ProxySelector.of(new InetSocketAddress(c.host(), c.port())) : null;
    }

    /** 变更侦测签名：WebFetcher 据此决定是否重建 HttpClient（直连为 "direct"）。 */
    public String signature() {
        Config c = current.get();
        return c.usable() ? c.host() + ":" + c.port() : "direct";
    }
}
