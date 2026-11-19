package com.ganzithon.moyang.ai.repository;

import com.ganzithon.moyang.ai.domain.ConversationMessage;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversationMessageRepository extends JpaRepository<ConversationMessage, Long> {
    List<ConversationMessage> findByConversationIdOrderByIdAsc(Long conversationId);

    Optional<ConversationMessage> findFirstByConversationIdOrderByIdDesc(Long conversationId);
}
