package com.agentx.rag.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface KbDocumentRepository extends JpaRepository<KbDocument, UUID> {
    List<KbDocument> findByKbIdOrderByCreatedAtDesc(UUID kbId);
}
