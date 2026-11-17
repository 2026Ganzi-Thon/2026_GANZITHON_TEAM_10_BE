package com.ganzithon.moyang.sheep.service;

import com.ganzithon.moyang.common.exception.*;
import com.ganzithon.moyang.sheep.domain.*;
import com.ganzithon.moyang.sheep.dto.*;
import com.ganzithon.moyang.sheep.repository.*;
import jakarta.persistence.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.*;

@Service @Transactional(readOnly=true)
public class SheepService {
    private static final ZoneId SEOUL=ZoneId.of("Asia/Seoul");
    private final SheepWalletRepository wallets;
    private final SheepTransactionRepository ledger;
    private final SheepRecordRewardRepository rewards;
    private final EntityManager entityManager;
    private final Clock clock;
    private final boolean demo;
    public SheepService(SheepWalletRepository wallets,SheepTransactionRepository ledger,SheepRecordRewardRepository rewards,
            EntityManager entityManager,Clock clock,@Value("${moyang.sheep.demo-daily-limit-off:${DEMO_DAILY_LIMIT_OFF:false}}") boolean demo){
        this.wallets=wallets;this.ledger=ledger;this.rewards=rewards;this.entityManager=entityManager;this.clock=clock;this.demo=demo;
    }
    public SheepBalanceResponse balance(long userId){validateUser(userId);return new SheepBalanceResponse(wallets.findById(userId).map(SheepWallet::getBalance).orElse(0));}
    /** Internal record-save participant. Caller must validate actual record ownership/completion BEFORE invoking. */
    @Transactional(propagation=Propagation.MANDATORY)
    public SheepAwardResponse awardRecord(long userId,long recordId){
        validateUser(userId);positive(recordId);
        var wallet=lockWallet(userId);var now=now();
        var decision=rewards.lockRecord(recordId);
        if(decision.isPresent()){
            if(decision.get().getUserId()!=userId)throw new BusinessException(ErrorCode.FORBIDDEN);
            return new SheepAwardResponse(0,wallet.getBalance());
        }
        SheepTransaction transaction=null;
        if(demo || ledger.lockDailyReward(userId,now.toLocalDate()).isEmpty()){
            var mode=demo?SheepRewardMode.DEMO:SheepRewardMode.DAILY;
            transaction=append(wallet,userId,SheepTransactionType.RECORD,Long.toString(recordId),null,mode,demo?null:now.toLocalDate(),now);
        }
        int earned=transaction==null?0:1;
        rewards.saveAndFlush(new SheepRecordReward(recordId,userId,now,earned,transaction));
        return new SheepAwardResponse(earned,wallet.getBalance());
    }
    @Transactional public SheepChangeResponse spendExtraChat(long userId,String referenceId){return use(userId,SheepTransactionType.AI_EXTRA_CHAT,referenceId,null);}
    @Transactional public SheepChangeResponse useMeetingCreate(long userId,long meetingId){positive(meetingId);return use(userId,SheepTransactionType.MEETING_CREATE,Long.toString(meetingId),meetingId);}
    @Transactional public SheepChangeResponse useMeetingJoin(long userId,long meetingId){positive(meetingId);return use(userId,SheepTransactionType.MEETING_JOIN,Long.toString(meetingId),meetingId);}
    /** Caller verifies cancellation policy in its own meeting transaction. */
    @Transactional public SheepChangeResponse refundMeeting(long userId,long meetingId){positive(meetingId);return use(userId,SheepTransactionType.MEETING_REFUND,Long.toString(meetingId),meetingId);}
    private SheepChangeResponse use(long userId,SheepTransactionType type,String referenceId,Long meetingId){
        validateUser(userId);validateReference(referenceId);
        var wallet=lockWallet(userId);
        var existing=ledger.lockSource(userId,type,referenceId);
        if(existing.isPresent())return new SheepChangeResponse(existing.get().getId(),0,wallet.getBalance(),false);
        if(type==SheepTransactionType.MEETING_REFUND && ledger.lockSource(userId,SheepTransactionType.MEETING_CREATE,referenceId).isEmpty()
                && ledger.lockSource(userId,SheepTransactionType.MEETING_JOIN,referenceId).isEmpty())throw new BusinessException(ErrorCode.REFUND_NOT_ELIGIBLE);
        if(type.amount()<0 && wallet.getBalance()<1)throw new BusinessException(ErrorCode.NOT_ENOUGH_WOOL);
        var tx=append(wallet,userId,type,referenceId,meetingId,SheepRewardMode.NONE,null,now());
        return new SheepChangeResponse(tx.getId(),type.amount(),wallet.getBalance(),true);
    }
    private SheepWallet lockWallet(long userId){
        wallets.ensureWallet(userId,now());
        var wallet=wallets.lock(userId).orElseThrow();
        // The caller may have read this wallet before another transaction updated it.
        // Refresh the managed instance after the DB lock, avoiding first-level cache lost updates.
        entityManager.refresh(wallet,LockModeType.PESSIMISTIC_WRITE);
        return wallet;
    }
    private SheepTransaction append(SheepWallet wallet,long userId,SheepTransactionType type,String referenceId,Long meetingId,SheepRewardMode mode,LocalDate date,LocalDateTime now){
        try{wallet.change(type.amount(),now);}catch(ArithmeticException e){throw new BusinessException(ErrorCode.SHEEP_BALANCE_LIMIT);}
        return ledger.saveAndFlush(new SheepTransaction(userId,type,referenceId,meetingId,wallet.getBalance(),mode,date,now));
    }
    public SheepTransactionListResponse transactions(long userId,Long cursor,int size){
        validateUser(userId);
        if(size<1||size>50||(cursor!=null&&cursor<1))throw new BusinessException(ErrorCode.INVALID_REQUEST);
        var rows=ledger.page(userId,cursor,PageRequest.of(0,size+1));boolean more=rows.size()>size;
        var selected=rows.subList(0,Math.min(size,rows.size()));
        var items=selected.stream().map(t->new SheepTransactionResponse(t.getId(),t.getType(),t.getAmount(),t.getBalanceAfter(),t.getReferenceId(),t.getMeetingId(),t.getCreatedAt().atZone(SEOUL).toOffsetDateTime())).toList();
        return new SheepTransactionListResponse(items,more?selected.get(selected.size()-1).getId():null,more);
    }
    private LocalDateTime now(){return LocalDateTime.ofInstant(clock.instant(),SEOUL);}
    private static void validateUser(long userId){positive(userId);}
    private static void positive(long id){if(id<1)throw new BusinessException(ErrorCode.INVALID_REQUEST);}
    private static void validateReference(String ref){if(ref==null||ref.isBlank()||ref.length()>160||!ref.equals(ref.strip()))throw new BusinessException(ErrorCode.INVALID_REQUEST);}
}
