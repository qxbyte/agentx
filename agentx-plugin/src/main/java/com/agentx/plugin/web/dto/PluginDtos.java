package com.agentx.plugin.web.dto;

import com.agentx.plugin.service.ManifestReader;
import com.agentx.plugin.service.PluginService;
import com.agentx.plugin.store.InstalledPlugin;
import com.agentx.plugin.store.KnownMarketplace;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

public final class PluginDtos {
    private PluginDtos() {}

    public record AddMarketplaceRequest(@NotBlank String source) {}

    public record InstallRequest(@NotBlank String name, @NotBlank String marketplace) {}

    public record EnabledRequest(boolean enabled) {}

    /** marketplace 视图:注册信息 + 清单内的可安装插件。 */
    public record MarketplaceView(String name, String sourceType, String locator,
                                  String installLocation, List<AvailableView> plugins) {
        public static MarketplaceView of(String name, KnownMarketplace mp, List<AvailableView> plugins) {
            return new MarketplaceView(name, mp.type(), mp.locator(), mp.installLocation(), plugins);
        }
    }

    /** marketplace 清单里的一个可安装插件(合并已安装状态)。 */
    public record AvailableView(String name, String description, String version,
                                String sourceType, boolean installed, Boolean enabled) {
        public static AvailableView of(ManifestReader.Entry entry, InstalledPlugin installed) {
            return new AvailableView(entry.name(), entry.description(), entry.version(),
                    entry.sourceType(), installed != null,
                    installed == null ? null : installed.enabled());
        }
    }

    /** 已安装插件视图;unsupported 为暂不支持的能力名单(如 ["hooks"])。 */
    public record InstalledView(String id, String name, String marketplace, String version,
                                String description, boolean enabled, String installedAt,
                                int skillCount, int agentCount, int mcpCount, List<String> unsupported) {
        public static InstalledView of(InstalledPlugin p, String description,
                                       PluginService.Capabilities caps) {
            return new InstalledView(p.id(), p.name(), p.marketplace(), p.version(),
                    description, p.enabled(), p.installedAt(),
                    caps.skillCount(), caps.agentCount(), caps.mcpCount(), caps.unsupported());
        }
    }
}
