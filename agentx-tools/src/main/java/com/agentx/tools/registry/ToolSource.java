package com.agentx.tools.registry;

import org.springframework.ai.tool.ToolCallback;
import java.util.List;

/**
 * 工具来源 SPI（设计文档 §4.5 三级注册）。
 * L1 = 代码级 {@link CodeToolSource}；L2 = MCP 远程（M5）；L3 = HTTP 动态（预留）。
 * 新来源实现本接口并注册为 bean，{@link ToolRegistry} 自动聚合。
 */
public interface ToolSource {

    /** 来源标识：CODE / MCP / HTTP。 */
    String origin();

    /** 当前可用的工具回调集合；实现方负责自身的连接/刷新语义。 */
    List<ToolCallback> tools();
}
