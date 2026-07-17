package com.agentx.agent.service;

import com.agentx.agent.domain.AgentDefinition;
import com.agentx.agent.domain.AgentDefinitionRepository;
import com.agentx.common.util.UuidV7;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 插件子 agent 注册面（plugin 模块调用）：把插件 agents/*.md 同步为只读 Agent 定义
 * （source=PLUGIN,name 带 "plugin:" 命名空间前缀）,随插件安装/启停/卸载联动。
 * 按 name upsert 保留 UUID——历史会话绑定的 agentId 在插件升级后仍有效。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PluginAgentRegistry {

    /** 插件 agent 的最小规格（name 已含命名空间前缀）。 */
    public record PluginAgentSpec(String name, String description, String systemPrompt) {}

    private final AgentDefinitionRepository repository;

    /** 全量同步某插件的 agents：upsert 现有名 + 清理已移除的名。 */
    @Transactional
    public void sync(String pluginId, List<PluginAgentSpec> specs, boolean enabled) {
        Set<String> names = specs.stream().map(PluginAgentSpec::name).collect(Collectors.toSet());
        for (AgentDefinition stale : repository.findByPluginId(pluginId)) {
            if (!names.contains(stale.getName())) {
                repository.delete(stale);
            }
        }
        for (PluginAgentSpec spec : specs) {
            var existing = repository.findByName(spec.name());
            if (existing.isPresent() && !"PLUGIN".equals(existing.get().getSource())) {
                log.warn("插件 agent {} 与用户 Agent 同名,跳过同步", spec.name());
                continue;
            }
            AgentDefinition agent = existing.orElseGet(() -> {
                AgentDefinition a = new AgentDefinition();
                a.setId(UuidV7.next());
                a.setName(spec.name());
                return a;
            });
            agent.setSource("PLUGIN");
            agent.setPluginId(pluginId);
            agent.setDescription(spec.description() == null ? "" : spec.description());
            agent.setSystemPrompt(spec.systemPrompt());
            agent.setEnabled(enabled);
            repository.save(agent);
        }
        log.info("插件 agents 已同步 plugin={} count={}", pluginId, specs.size());
    }

    /** 插件启停 → 联动其贡献的全部 agent。 */
    @Transactional
    public void setEnabled(String pluginId, boolean enabled) {
        for (AgentDefinition agent : repository.findByPluginId(pluginId)) {
            agent.setEnabled(enabled);
            repository.save(agent);
        }
    }

    /** 插件卸载 → 移除其贡献的全部 agent。 */
    @Transactional
    public void remove(String pluginId) {
        repository.deleteAll(repository.findByPluginId(pluginId));
    }
}
