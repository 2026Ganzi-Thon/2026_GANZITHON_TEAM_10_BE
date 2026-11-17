package com.ganzithon.moyang.sheep.repository;
import com.ganzithon.moyang.sheep.domain.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import java.time.LocalDate;
import java.util.*;
public interface SheepTransactionRepository extends JpaRepository<SheepTransaction,Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from SheepTransaction t where t.userId=:uid and t.type=:type and t.referenceId=:ref")
    Optional<SheepTransaction> lockSource(@Param("uid") long uid,@Param("type") SheepTransactionType type,@Param("ref") String ref);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from SheepTransaction t where t.userId=:uid and t.type=com.ganzithon.moyang.sheep.domain.SheepTransactionType.RECORD and t.rewardMode=com.ganzithon.moyang.sheep.domain.SheepRewardMode.DAILY and t.rewardDate=:date")
    Optional<SheepTransaction> lockDailyReward(@Param("uid") long uid,@Param("date") LocalDate date);
    @Query("select t from SheepTransaction t where t.userId=:uid and (:cursor is null or t.id<:cursor) order by t.id desc")
    List<SheepTransaction> page(@Param("uid") long uid,@Param("cursor") Long cursor,Pageable page);
}
