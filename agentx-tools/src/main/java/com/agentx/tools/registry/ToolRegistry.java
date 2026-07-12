package com.agentx.tools.registry;

import com.agentx.common.api.ErrorCode;
import com.agentx.common.exception.BizException;
import com.agentx.common.util.UuidV7;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具注册中心（设计文档 §4.5）：聚合全部 {@link ToolSource}，
 * 提供名称索引、启停运营开关（持久化）与按名解析。
 * Agent 侧经 {@link #resolve(List)} 取回调集合，禁用/缺失的工具被跳过并告警。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ToolRegistry {

    private final List<ToolSource> sources;
    private final ToolStateRepository stateRepository;

    /** 全量工具（name → callback），每次现取：MCP 等动态来源的可用性由来源自身维护。 */
    public Map<String, ToolCallback> all() {
        Map<String, ToolCallback> map = new LinkedHashMap<>();
        for (ToolSource source : sources) {
            for (ToolCallback cb : source.tools()) {
                map.putIfAbsent(cb.getToolDefinition().name(), cb);
            }
        }
        return map;
    }

    /** 工具目录（管理端视图）：运行时定义 + 持久化启停状态，缺失的状态行自动补建。 */
    @Transactional
    public List<ToolState> catalog() {
        List<ToolState> result = new ArrayList<>();
        for (ToolSource source : sources) {
            for (ToolCallback cb : source.tools()) {
                var def = cb.getToolDefinition();
                ToolState state = stateRepository.findByName(def.name()).orElseGet(() -> {
                    ToolState s = new ToolState();
                    s.setId(UuidV7.next());
                    s.setName(def.name());
                    return s;
                });
                state.setSource(source.origin());
                state.setDescription(def.description() == null ? "" : def.description());
                state.setParamsSchema(def.inputSchema());
                result.add(stateRepository.save(state));
            }
        }
        return result;
    }

    @Transactional
    public ToolState setEnabled(String name, boolean enabled) {
        ToolState state = stateRepository.findByName(name)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "工具不存在: " + name));
        state.setEnabled(enabled);
        return stateRepository.save(state);
    }

    /** 按名解析为可执行回调；禁用或不可用的跳过（记录告警），保证 Agent 不因单个工具失联而不可用。 */
    public List<ToolCallback> resolve(List<String> names) {
        if (names == null || names.isEmpty()) {
            return List.of();
        }
        Map<String, ToolCallback> available = all();
        List<ToolCallback> result = new ArrayList<>();
        for (String name : names) {
            ToolCallback cb = available.get(name);
            boolean enabled = stateRepository.findByName(name).map(ToolState::isEnabled).orElse(true);
            if (cb == null || !enabled) {
                log.warn("工具不可用或已禁用，跳过: {}", name);
                continue;
            }
            result.add(cb);
        }
        return result;
    }
}
