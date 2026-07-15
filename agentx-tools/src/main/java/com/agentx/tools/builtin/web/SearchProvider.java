package com.agentx.tools.builtin.web;

import java.util.List;

/**
 * 网络搜索数据来源（策略接口）：webSearch 工具与具体搜索服务解耦。
 * 默认实现为 {@link BingSearchProvider}（免 key 抓取）；
 * 需要更稳定的服务时按此接口接入博查 / Tavily 等 API 即可，工具层不动。
 */
public interface SearchProvider {

    record SearchResult(String title, String url, String snippet) {}

    /**
     * 执行搜索。
     *
     * @param query 关键词
     * @param topN  期望条数（1-10）
     * @return 结果列表，可能为空（无结果或解析失败）
     */
    List<SearchResult> search(String query, int topN) throws Exception;
}
