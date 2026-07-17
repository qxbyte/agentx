package com.agentx.plugin.store;

/**
 * known_marketplaces.json 的一条记录。
 * type: github(owner/repo) | url(git https url) | path(本地绝对路径,不 clone 直接引用)。
 */
public record KnownMarketplace(
        String type,
        String locator,
        String installLocation,
        String lastUpdated
) {}
