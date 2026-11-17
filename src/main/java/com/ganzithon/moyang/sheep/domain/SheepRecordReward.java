package com.ganzithon.moyang.sheep.domain;
import jakarta.persistence.*;
import org.hibernate.annotations.Check;
import java.time.*;
/** Decision for every record save, including earned=0; not shown as a wallet transaction. */
@Entity @Table(name="sheep_record_rewards",indexes=@Index(name="idx_sheep_record_user_date",columnList="user_id,decided_date"))
@Check(constraints="(sheep_earned=0 and transaction_id is null) or (sheep_earned=1 and transaction_id is not null)")
public class SheepRecordReward {
    @Id @Column(name="record_id") private Long recordId;
    @Column(nullable=false) private long userId;
    @Column(nullable=false) private LocalDate decidedDate;
    @Column(nullable=false) private int sheepEarned;
    @OneToOne(fetch=FetchType.LAZY) @JoinColumn(name="transaction_id",unique=true) private SheepTransaction transaction;
    @Column(nullable=false) private LocalDateTime createdAt;
    protected SheepRecordReward(){}
    public SheepRecordReward(long recordId,long userId,LocalDateTime now,int earned,SheepTransaction transaction){
        this.recordId=recordId;this.userId=userId;this.decidedDate=now.toLocalDate();this.sheepEarned=earned;this.transaction=transaction;this.createdAt=now;
    }
    public long getUserId(){return userId;}
}
