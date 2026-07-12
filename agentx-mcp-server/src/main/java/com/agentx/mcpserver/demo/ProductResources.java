package com.agentx.mcpserver.demo;

import org.springframework.ai.mcp.annotation.McpResource;
import org.springframework.stereotype.Service;

/** @McpResource 示例：把内部文档/配置暴露为 MCP 资源。 */
@Service
public class ProductResources {

    @McpResource(uri = "agentx://products/manual",
            name = "产品手册", description = "AgentX 演示产品手册（纯文本）")
    public String productManual() {
        return """
                AgentX 演示产品手册
                1. 机械键盘：87 键热插拔，保修 2 年。
                2. 显示器：27 寸 4K，支持 65W 反向供电。
                退换货政策：签收 7 日内无理由退货。""";
    }
}
