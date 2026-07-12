package com.agentx.infra.ai.client;

import java.util.UUID;

/** 模型配置变更事件：更新/删除/默认切换时发布，用于驱逐 ChatClient 缓存。 */
public record ModelConfigChangedEvent(UUID configId) {}
