package com.agentx.coding.runtime;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 本地"天然工具"配置：普通对话的沙箱根 / 编码会话只读越区的第二根,共用一处。 */
@Getter
@Component
public class LocalToolsSettings {

    private final boolean enabled;
    private final String root;

    public LocalToolsSettings(@Value("${agentx.local-tools.enabled:true}") boolean enabled,
                              @Value("${agentx.local-tools.root:${user.home}}") String root) {
        this.enabled = enabled;
        this.root = root;
    }
}
