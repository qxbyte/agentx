package com.agentx.agent.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AgentDefinitionRepository extends JpaRepository<AgentDefinition, UUID> {
    List<AgentDefinition> findByEnabledTrueOrderByCreatedAtAsc();
    Optional<AgentDefinition> findByName(String name);
}
