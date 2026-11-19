package com.ganzithon.moyang.meeting.repository;

import com.ganzithon.moyang.meeting.domain.MeetingEnums.MeetingType;
import com.ganzithon.moyang.meeting.domain.MeetingEnums.MessageType;
import com.ganzithon.moyang.meeting.domain.MeetingMessage;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MeetingMessageRepository extends JpaRepository<MeetingMessage, Long> {

    List<MeetingMessage> findByMeetingIdOrderByIdAsc(long meetingId);

    /** 폴링: 이 번호보다 뒤의 메시지만, 오래된 순서로. */
    List<MeetingMessage> findByMeetingIdAndIdGreaterThanOrderByIdAsc(long meetingId, long afterId);

    @Query("select count(distinct m.userId) from MeetingMessage m where m.meetingId = :meetingId and m.type = :type")
    long countDistinctSenders(@Param("meetingId") long meetingId, @Param("type") MessageType type);

    /** 그림을 낸 사람 수(그림 단계 자동 진행 판단용). */
    default long countDrawingSubmitters(long meetingId) {
        return countDistinctSenders(meetingId, MessageType.DRAWING);
    }

    /**
     * 이 그림이 사용자가 참여 중인 모임 채팅에 공유돼 있는지 센다. 종료 후 24시간이 지난 방(visibleSince보다 일찍 끝난 모임)은
     * 채팅이 삭제된 것으로 보고 세지 않는다. 예약 모임은 입장한 사람만 본다(live는 실시간 모임 종류 값).
     */
    @Query("select count(m) from MeetingMessage m, MeetingParticipant p, Meeting mt "
            + "where m.drawingId = :drawingId and m.type = :type "
            + "and p.meetingId = m.meetingId and p.userId = :userId and p.canceledAt is null "
            + "and mt.id = m.meetingId and (mt.endedAt is null or mt.endedAt >= :visibleSince) "
            + "and (mt.type = :live or p.enteredAt is not null)")
    long countSharedWith(@Param("drawingId") long drawingId, @Param("userId") long userId,
                         @Param("type") MessageType type, @Param("visibleSince") LocalDateTime visibleSince,
                         @Param("live") MeetingType live);
}
