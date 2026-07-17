package com.agentx.tools.files;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface GeneratedFileRepository extends JpaRepository<GeneratedFile, UUID> {
    Optional<GeneratedFile> findByIdAndUserId(UUID id, UUID userId);
}
