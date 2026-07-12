# agentx-mcp-server — MCP Server 模板

企业把存量系统包装为 MCP server 的参考实现（独立 Spring Boot 应用，与主平台解耦部署）。

## 提供的示例能力

| 类型 | 注解 | 示例 |
|---|---|---|
| 工具 | `@McpTool` | `queryOrder` / `listRecentOrders`（模拟订单系统） |
| 资源 | `@McpResource` | `agentx://products/manual`（产品手册） |
| 提示词 | `@McpPrompt` | `ticket-reply`（客服回复模板） |

## 运行

```bash
# Streamable HTTP（默认，端口 8090，MCP 端点 /mcp）
mvn -pl agentx-mcp-server spring-boot:run

# stdio 模式（被客户端以子进程方式拉起）
mvn -pl agentx-mcp-server spring-boot:run -Dspring-boot.run.profiles=stdio
```

## 接入 AgentX 平台

管理后台 → MCP 服务 → 新增：

```json
{
  "name": "demo-orders",
  "transport": "STREAMABLE_HTTP",
  "connectParams": { "url": "http://localhost:8090" },
  "enabled": true
}
```

测试连接通过后，远程工具自动进入平台工具注册中心（来源 MCP），
在 Agent 定义里勾选即可被模型调用。

## 企业包装指南

1. 复制本模块，改名（如 `crm-mcp-server`）。
2. 把 demo 类中的 mock 数据换成对内部系统的 RestClient/RPC 调用。
3. `@McpTool` 的 description 写清楚能力与参数语义——这是模型选择工具的唯一依据。
4. 生产部署建议加鉴权（网关层 API-Key / OAuth2），MCP 端点不要裸暴露公网。
