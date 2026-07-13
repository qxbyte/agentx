package com.agentx.rag.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface ExternalKbRepository extends JpaRepository<ExternalKb, UUID> {
    List<ExternalKb> findByEnabledTrueOrderByCreatedAtAsc();
    List<ExternalKb> findAllByOrderByCreatedAtAsc();
}
