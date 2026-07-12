package com.agentx.rag.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface RagIngestTaskRepository extends JpaRepository<RagIngestTask, UUID> {
    Optional<RagIngestTask> findFirstByDocIdOrderByCreatedAtDesc(UUID docId);
}
