package com.agentx.tools.registry;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import java.util.Arrays;
import java.util.List;

/**
 * L1 代码级工具来源：收集所有 @AgentTool bean 的 @Tool 方法。
 * 结果在首次访问时物化并缓存——代码级工具集在运行期不变。
 */
@Component
@RequiredArgsConstructor
public class CodeToolSource implements ToolSource {

    private final ApplicationContext applicationContext;
    private volatile List<ToolCallback> cached;

    @Override
    public String origin() {
        return "CODE";
    }

    @Override
    public List<ToolCallback> tools() {
        List<ToolCallback> result = cached;
        if (result == null) {
            Object[] beans = applicationContext.getBeansWithAnnotation(AgentTool.class)
                    .values().toArray();
            result = beans.length == 0 ? List.of() : Arrays.asList(
                    MethodToolCallbackProvider.builder().toolObjects(beans).build().getToolCallbacks());
            cached = result;
        }
        return result;
    }
}
