package com.ganzithon.moyang.sheep.domain;
import jakarta.persistence.*;
import org.hibernate.annotations.Check;
import java.time.*;
@Entity
@Table(name="sheep_transactions",uniqueConstraints={
    @UniqueConstraint(name="uk_sheep_source",columnNames={"user_id","transaction_type","reference_id"}),
    @UniqueConstraint(name="uk_sheep_meeting",columnNames={"user_id","meeting_id","transaction_type"}),
    @UniqueConstraint(name="uk_sheep_daily_reward",columnNames={"user_id","transaction_type","reward_date"})},
    indexes=@Index(name="idx_sheep_user_id",columnList="user_id,id"))
@Check(constraints="balance_after >= 0 and ((transaction_type='RECORD' and amount=1 and ((reward_mode='DAILY' and reward_date is not null) or (reward_mode='DEMO' and reward_date is null))) or (transaction_type='MEETING_REFUND' and amount=1 and reward_mode='NONE' and reward_date is null) or (transaction_type in ('MEETING_CREATE','MEETING_JOIN','AI_EXTRA_CHAT') and amount=-1 and reward_mode='NONE' and reward_date is null)) and ((transaction_type in ('MEETING_CREATE','MEETING_JOIN','MEETING_REFUND') and meeting_id is not null) or (transaction_type in ('RECORD','AI_EXTRA_CHAT') and meeting_id is null))")
public class SheepTransaction {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false) private long userId;
    @Enumerated(EnumType.STRING) @Column(name="transaction_type",nullable=false,length=32) private SheepTransactionType type;
    @Column(nullable=false,length=160) private String referenceId;
    private Long meetingId;
    @Column(nullable=false) private int amount;
    @Column(nullable=false) private int balanceAfter;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=8) private SheepRewardMode rewardMode;
    private LocalDate rewardDate;
    @Column(nullable=false) private LocalDateTime createdAt;
    protected SheepTransaction(){}
    public SheepTransaction(long userId,SheepTransactionType type,String referenceId,Long meetingId,int balanceAfter,SheepRewardMode mode,LocalDate rewardDate,LocalDateTime now){
        this.userId=userId;this.type=type;this.referenceId=referenceId;this.meetingId=meetingId;this.amount=type.amount();
        this.balanceAfter=balanceAfter;this.rewardMode=mode;this.rewardDate=rewardDate;this.createdAt=now;
    }
    public Long getId(){return id;} public SheepTransactionType getType(){return type;}
    public String getReferenceId(){return referenceId;} public Long getMeetingId(){return meetingId;}
    public int getAmount(){return amount;} public int getBalanceAfter(){return balanceAfter;}
    public LocalDateTime getCreatedAt(){return createdAt;}
}
