package com.agentx.plugin.web;

import com.agentx.common.api.ApiResponse;
import com.agentx.plugin.service.ManifestReader;
import com.agentx.plugin.service.MarketplaceService;
import com.agentx.plugin.service.PluginService;
import com.agentx.plugin.store.InstalledPlugin;
import com.agentx.plugin.web.dto.PluginDtos.AddMarketplaceRequest;
import com.agentx.plugin.web.dto.PluginDtos.AvailableView;
import com.agentx.plugin.web.dto.PluginDtos.EnabledRequest;
import com.agentx.plugin.web.dto.PluginDtos.InstallRequest;
import com.agentx.plugin.web.dto.PluginDtos.InstalledView;
import com.agentx.plugin.web.dto.PluginDtos.MarketplaceView;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 插件与 marketplace 管理(本机目录化配置,登录即可用)。 */
@RestController
@RequiredArgsConstructor
public class PluginController {

    private final MarketplaceService marketplaces;
    private final PluginService plugins;
    private final ManifestReader manifests;

    @PostMapping("/api/v1/plugins/marketplaces")
    public ApiResponse<MarketplaceView> addMarketplace(@Valid @RequestBody AddMarketplaceRequest req) {
        String name = marketplaces.add(req.source());
        return ApiResponse.ok(marketplaceView(name));
    }

    @GetMapping("/api/v1/plugins/marketplaces")
    public ApiResponse<List<MarketplaceView>> listMarketplaces() {
        List<MarketplaceView> views = new ArrayList<>();
        for (String name : marketplaces.list().keySet()) {
            views.add(marketplaceView(name));
        }
        return ApiResponse.ok(views);
    }

    @PostMapping("/api/v1/plugins/marketplaces/{name}/update")
    public ApiResponse<MarketplaceView> updateMarketplace(@PathVariable String name) {
        marketplaces.update(name);
        return ApiResponse.ok(marketplaceView(name));
    }

    @DeleteMapping("/api/v1/plugins/marketplaces/{name}")
    public ApiResponse<Void> removeMarketplace(@PathVariable String name) {
        marketplaces.remove(name);
        return ApiResponse.ok();
    }

    @PostMapping("/api/v1/plugins/install")
    public ApiResponse<InstalledView> install(@Valid @RequestBody InstallRequest req) {
        return ApiResponse.ok(installedView(plugins.install(req.name(), req.marketplace())));
    }

    @GetMapping("/api/v1/plugins")
    public ApiResponse<List<InstalledView>> listInstalled() {
        return ApiResponse.ok(plugins.list().values().stream().map(this::installedView).toList());
    }

    @PatchMapping("/api/v1/plugins/{id}/enabled")
    public ApiResponse<InstalledView> setEnabled(@PathVariable String id,
                                                 @RequestBody EnabledRequest req) {
        return ApiResponse.ok(installedView(plugins.setEnabled(id, req.enabled())));
    }

    @PostMapping("/api/v1/plugins/{id}/update")
    public ApiResponse<InstalledView> update(@PathVariable String id) {
        return ApiResponse.ok(installedView(plugins.update(id)));
    }

    @DeleteMapping("/api/v1/plugins/{id}")
    public ApiResponse<Void> uninstall(@PathVariable String id) {
        plugins.uninstall(id);
        return ApiResponse.ok();
    }

    /* ---------- 视图组装 ---------- */

    private MarketplaceView marketplaceView(String name) {
        var mp = marketplaces.getOrThrow(name);
        Map<String, InstalledPlugin> installed = plugins.list();
        List<AvailableView> available = marketplaces.manifest(name)
                .map(m -> m.plugins().stream()
                        .map(e -> AvailableView.of(e, installed.get(e.name() + "@" + name)))
                        .toList())
                .orElse(List.of());
        return MarketplaceView.of(name, mp, available);
    }

    private InstalledView installedView(InstalledPlugin plugin) {
        String description = manifests.readPluginMeta(Path.of(plugin.installPath()))
                .map(ManifestReader.PluginMeta::description)
                .orElse("");
        return InstalledView.of(plugin, description, plugins.capabilities(plugin));
    }
}
