package com.ganzithon.moyang.meeting.repository;

import com.ganzithon.moyang.meeting.domain.Meeting;
import com.ganzithon.moyang.meeting.domain.MeetingEnums.MeetingStatus;
import com.ganzithon.moyang.meeting.domain.MeetingEnums.MeetingType;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MeetingRepository extends JpaRepository<Meeting, Long> {
    /** 모임 행을 잠그고 읽는다. 락 순서는 항상 모임 행 → 양털 지갑. 정원 확인과 참여 저장이 겹치지 않게 한다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Meeting m where m.id = :id")
    Optional<Meeting> findForUpdate(@Param("id") Long id);

    List<Meeting> findByTypeAndStatusIn(MeetingType type, Collection<MeetingStatus> statuses);
}
