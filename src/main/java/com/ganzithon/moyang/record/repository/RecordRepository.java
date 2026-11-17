package com.ganzithon.moyang.record.repository;

import com.ganzithon.moyang.record.domain.ReflectionRecord;
import com.ganzithon.moyang.record.domain.ReflectionRecord.Status;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import java.time.*;
import java.util.*;

public interface RecordRepository extends JpaRepository<ReflectionRecord,Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ReflectionRecord r where r.id=:id")
    Optional<ReflectionRecord> lock(@Param("id") long id);

    @Query("select r from ReflectionRecord r where r.userId=:uid and r.status=:status and (:date is null or r.recordDate=:date) and (:time is null or r.completedAt<:time or (r.completedAt=:time and r.id<:id)) order by r.completedAt desc,r.id desc")
    List<ReflectionRecord> page(@Param("uid") long uid,@Param("status") Status status,@Param("date") LocalDate date,
        @Param("time") LocalDateTime time,@Param("id") Long id,Pageable page);

    @Query("select r from ReflectionRecord r where r.userId=:uid and r.status=:status and r.recordDate>=:start and r.recordDate<:end and not exists (select n.id from ReflectionRecord n where n.userId=r.userId and n.status=:status and n.recordDate=r.recordDate and (n.completedAt>r.completedAt or (n.completedAt=r.completedAt and n.id>r.id))) order by r.recordDate")
    List<ReflectionRecord> calendar(@Param("uid") long uid,@Param("status") Status status,@Param("start") LocalDate start,@Param("end") LocalDate end);
}
