package com.ganzithon.moyang.sheep.repository;
import com.ganzithon.moyang.sheep.domain.SheepRecordReward;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
public interface SheepRecordRewardRepository extends JpaRepository<SheepRecordReward,Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select r from SheepRecordReward r where r.recordId=:id")
    Optional<SheepRecordReward> lockRecord(@Param("id") long id);
}
