package com.agentx.infra.ai.audit;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface AiCallLogRepository extends JpaRepository<AiCallLog, UUID> {
}
