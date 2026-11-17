package com.ganzithon.moyang.meeting.repository;

import com.ganzithon.moyang.meeting.domain.MeetingParticipant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface MeetingParticipantRepository extends JpaRepository<MeetingParticipant, Long> {

    interface MeetingCount {
        Long getMeetingId();

        Long getCnt();
    }

    long countByMeetingId(long meetingId);

    Optional<MeetingParticipant> findByMeetingIdAndUserId(long meetingId, long userId);

    List<MeetingParticipant> findByUserId(long userId);

    List<MeetingParticipant> findByMeetingIdInAndUserId(Collection<Long> meetingIds, long userId);

    @Query("select p.meetingId as meetingId, count(p) as cnt from MeetingParticipant p "
            + "where p.meetingId in :ids group by p.meetingId")
    List<MeetingCount> countByMeetingIds(Collection<Long> ids);
}
