package com.agentx.chat.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChatConversationRepository extends JpaRepository<ChatConversation, UUID> {
    List<ChatConversation> findByUserIdOrderByUpdatedAtDesc(UUID userId);
    Optional<ChatConversation> findByIdAndUserId(UUID id, UUID userId);
}
