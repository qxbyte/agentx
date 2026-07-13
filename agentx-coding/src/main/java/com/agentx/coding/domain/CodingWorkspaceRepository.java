package com.agentx.coding.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CodingWorkspaceRepository extends JpaRepository<CodingWorkspace, UUID> {
    List<CodingWorkspace> findByUserIdOrderByCreatedAtDesc(UUID userId);
    Optional<CodingWorkspace> findByIdAndUserId(UUID id, UUID userId);
}
