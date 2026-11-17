package com.ganzithon.moyang.ai.repository;

import com.ganzithon.moyang.ai.domain.MeetingCard;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MeetingCardRepository extends JpaRepository<MeetingCard, Long> {
    Optional<MeetingCard> findByMeetingIdAndUserId(Long meetingId, Long userId);

    boolean existsByMeetingIdAndUserId(Long meetingId, Long userId);

    Optional<MeetingCard> findFirstByRecordIdOrderByIdDesc(Long recordId);
}
