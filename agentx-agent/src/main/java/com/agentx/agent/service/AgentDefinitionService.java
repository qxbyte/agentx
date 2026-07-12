package com.agentx.agent.service;

import com.agentx.agent.domain.AgentDefinition;
import com.agentx.agent.domain.AgentDefinitionRepository;
import com.agentx.common.api.ErrorCode;
import com.agentx.common.exception.BizException;
import com.agentx.common.util.UuidV7;
import com.agentx.agent.web.dto.AgentDtos.UpsertRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AgentDefinitionService {

    private final AgentDefinitionRepository repository;
    private final ObjectMapper objectMapper;

    public List<AgentDefinition> listEnabled() {
        return repository.findByEnabledTrueOrderByCreatedAtAsc();
    }

    public List<AgentDefinition> listAll() {
        return repository.findAll();
    }

    public AgentDefinition getEnabled(UUID id) {
        AgentDefinition agent = repository.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "Agent 不存在"));
        if (!agent.isEnabled()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "Agent 已禁用: " + agent.getName());
        }
        return agent;
    }

    /** tool_names JSON 数组 → 名称列表（空安全）。 */
    public List<String> toolNamesOf(AgentDefinition agent) {
        if (agent.getToolNames() == null || agent.getToolNames().isBlank()) {
            return List.of();
        }
        return objectMapper.readValue(agent.getToolNames(), new TypeReference<List<String>>() {});
    }

    /** kb_ids JSON 数组 → UUID 列表（空安全）。 */
    public List<UUID> kbIdsOf(AgentDefinition agent) {
        if (agent.getKbIds() == null || agent.getKbIds().isBlank()) {
            return List.of();
        }
        return objectMapper.readValue(agent.getKbIds(), new TypeReference<List<UUID>>() {});
    }

    @Transactional
    public AgentDefinition create(UpsertRequest req) {
        repository.findByName(req.name()).ifPresent(a -> {
            throw new BizException(ErrorCode.CONFLICT, "同名 Agent 已存在");
        });
        AgentDefinition agent = new AgentDefinition();
        agent.setId(UuidV7.next());
        apply(agent, req);
        return repository.save(agent);
    }

    @Transactional
    public AgentDefinition update(UUID id, UpsertRequest req) {
        AgentDefinition agent = repository.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "Agent 不存在"));
        apply(agent, req);
        return repository.save(agent);
    }

    @Transactional
    public void delete(UUID id) {
        repository.deleteById(id);
    }

    private void apply(AgentDefinition agent, UpsertRequest req) {
        agent.setName(req.name());
        agent.setDescription(req.description() == null ? "" : req.description());
        agent.setSystemPrompt(req.systemPrompt());
        agent.setWorkflowType(req.workflowType());
        agent.setToolNames(req.toolNames() == null ? null
                : objectMapper.writeValueAsString(req.toolNames()));
        agent.setKbIds(req.kbIds() == null ? null
                : objectMapper.writeValueAsString(req.kbIds()));
        agent.setModelConfigId(req.modelConfigId());
        agent.setMaxIterations(req.maxIterations() == null ? 8 : req.maxIterations());
        agent.setEnabled(req.enabled());
    }
}
