package com.agentx.rag.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface KnowledgeBaseRepository extends JpaRepository<KnowledgeBase, UUID> {
    List<KnowledgeBase> findByUserIdOrderByCreatedAtDesc(UUID userId);
    Optional<KnowledgeBase> findByIdAndUserId(UUID id, UUID userId);
}
