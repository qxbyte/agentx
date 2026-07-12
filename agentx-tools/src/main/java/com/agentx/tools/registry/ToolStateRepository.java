package com.agentx.tools.registry;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface ToolStateRepository extends JpaRepository<ToolState, UUID> {
    Optional<ToolState> findByName(String name);
}
