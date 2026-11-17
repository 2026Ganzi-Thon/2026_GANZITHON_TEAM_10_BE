package com.ganzithon.moyang.ai.repository;

import com.ganzithon.moyang.ai.domain.Conversation;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {
    Optional<Conversation> findByRecordId(Long recordId);

    Optional<Conversation> findFirstByUserIdAndStatusOrderByIdDesc(Long userId, Conversation.Status status);
}
