package com.agentx.rag.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface KbSegmentRepository extends JpaRepository<KbSegment, UUID> {
    List<KbSegment> findByDocIdOrderBySeqNoAsc(UUID docId);
    List<KbSegment> findByDocIdAndEnabledTrueOrderBySeqNoAsc(UUID docId);
    void deleteByDocId(UUID docId);
    long countByDocId(UUID docId);
}
