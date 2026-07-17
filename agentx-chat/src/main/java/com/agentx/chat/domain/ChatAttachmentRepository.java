package com.agentx.chat.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface ChatAttachmentRepository extends JpaRepository<ChatAttachment, UUID> {
    List<ChatAttachment> findByIdInAndUserIdAndMessageIdIsNull(List<UUID> ids, UUID userId);

    java.util.Optional<ChatAttachment> findByIdAndUserId(UUID id, UUID userId);
}
