package com.agentx.plugin.store;

/** installed_plugins.json 的一条记录（key 为 "name@marketplace"）。 */
public record InstalledPlugin(
        String name,
        String marketplace,
        String installPath,
        String version,
        boolean enabled,
        String installedAt,
        String gitCommitSha
) {
    public String id() {
        return name + "@" + marketplace;
    }

    public InstalledPlugin withEnabled(boolean value) {
        return new InstalledPlugin(name, marketplace, installPath, version, value, installedAt, gitCommitSha);
    }
}
