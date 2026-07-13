package com.agentx.infra.ai.model;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ModelConfigRepository extends JpaRepository<ModelConfig, UUID> {
    Optional<ModelConfig> findFirstByTypeAndDefaultModelTrueAndEnabledTrue(ModelType type);
    List<ModelConfig> findByTypeAndDefaultModelTrue(ModelType type);
    List<ModelConfig> findByTypeAndEnabledTrueOrderByDefaultModelDescNameAsc(ModelType type);
    Optional<ModelConfig> findByName(String name);
}
