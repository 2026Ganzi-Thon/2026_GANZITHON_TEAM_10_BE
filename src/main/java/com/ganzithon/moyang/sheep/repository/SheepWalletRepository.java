package com.ganzithon.moyang.sheep.repository;
import com.ganzithon.moyang.sheep.domain.SheepWallet;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.Optional;
public interface SheepWalletRepository extends JpaRepository<SheepWallet,Long> {
    @Modifying
    @Query(value="insert into sheep_wallets(user_id,balance,created_at,updated_at) values(:uid,0,:now,:now) on duplicate key update user_id=user_id",nativeQuery=true)
    int ensureWallet(@Param("uid") long uid,@Param("now") LocalDateTime now);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select w from SheepWallet w where w.userId=:uid")
    Optional<SheepWallet> lock(@Param("uid") long uid);
}
