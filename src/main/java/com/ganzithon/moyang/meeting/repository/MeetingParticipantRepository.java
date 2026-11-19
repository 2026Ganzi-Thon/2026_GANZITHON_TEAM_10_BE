package com.ganzithon.moyang.meeting.repository;

import com.ganzithon.moyang.meeting.domain.MeetingParticipant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** 개수·목록 조회는 기본적으로 "참여 중"(취소하지 않은) 사람만 센다. 취소한 행은 findByMeetingIdAndUserId로만 보인다. */
public interface MeetingParticipantRepository extends JpaRepository<MeetingParticipant, Long> {

    interface MeetingCount {
        Long getMeetingId();

        Long getCnt();
    }

    @Query("select count(p) from MeetingParticipant p where p.meetingId = :meetingId and p.canceledAt is null")
    long countActiveByMeetingId(long meetingId);

    /** 취소한 사람도 포함해서 찾는다(재신청 막기용). */
    Optional<MeetingParticipant> findByMeetingIdAndUserId(long meetingId, long userId);

    List<MeetingParticipant> findByMeetingIdAndCanceledAtIsNull(long meetingId);

    /** 참여 중이면서 입장한 사람, 먼저 입장한 순서대로(주제 건너뛰기 권한자 판단용). */
    List<MeetingParticipant> findByMeetingIdAndCanceledAtIsNullAndEnteredAtIsNotNullOrderByEnteredAtAscIdAsc(
            long meetingId);

    long countByMeetingIdAndCanceledAtIsNullAndEnteredAtIsNotNull(long meetingId);

    List<MeetingParticipant> findByUserIdAndCanceledAtIsNull(long userId);

    List<MeetingParticipant> findByMeetingIdInAndUserIdAndCanceledAtIsNull(Collection<Long> meetingIds, long userId);

    @Query("select p.meetingId as meetingId, count(p) as cnt from MeetingParticipant p "
            + "where p.meetingId in :ids and p.canceledAt is null group by p.meetingId")
    List<MeetingCount> countByMeetingIds(Collection<Long> ids);
}
