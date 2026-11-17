package com.ganzithon.moyang.sheep;

import com.ganzithon.moyang.common.exception.*;
import com.ganzithon.moyang.sheep.domain.*;
import com.ganzithon.moyang.sheep.dto.*;
import com.ganzithon.moyang.sheep.repository.*;
import com.ganzithon.moyang.sheep.service.SheepService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:sheep-service;MODE=MySQL;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test") @Import(SheepServiceTest.Fixtures.class)
class SheepServiceTest {
    @Autowired SheepService sheep;
    @Autowired SheepWalletRepository wallets;
    @Autowired SheepTransactionRepository ledger;
    @Autowired SheepRecordRewardRepository rewards;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired TestClock clock;
    static final AtomicLong USERS=new AtomicLong(1000),RECORDS=new AtomicLong(10000);
    static final Instant BASE=Instant.parse("2026-11-17T03:00:00Z");
    @BeforeEach void resetClock(){clock.current.set(BASE);}
    long user(){return USERS.incrementAndGet();}
    long record(){return RECORDS.incrementAndGet();}
    SheepAwardResponse award(long user,long record){return new TransactionTemplate(transactionManager).execute(s->sheep.awardRecord(user,record));}
    void fund(long uid,int amount){try{for(int i=0;i<amount;i++){clock.current.set(BASE.plusSeconds(i*86400L));award(uid,record());}}finally{clock.current.set(BASE);}}
    void code(Runnable run,ErrorCode expected){assertThatThrownBy(run::run).isInstanceOfSatisfying(BusinessException.class,e->assertThat(e.getErrorCode()).isEqualTo(expected));}

    @Test void missingWalletReadsZeroWithoutCreatingRows(){long uid=user();assertThat(sheep.balance(uid).balance()).isZero();assertThat(wallets.existsById(uid)).isFalse();assertThat(sheep.transactions(uid,null,20).items()).isEmpty();}
    @Test void firstDailySaveAwardsOneLaterSavesAwardZero(){long uid=user();var a=award(uid,record());var b=award(uid,record());var c=award(uid,record());assertThat(List.of(a.sheepEarned(),b.sheepEarned(),c.sheepEarned())).containsExactly(1,0,0);assertThat(c.sheepBalance()).isEqualTo(1);assertThat(sheep.transactions(uid,null,20).items()).hasSize(1);}
    @Test void sameRecordNeverReceivesSecondRewardEvenNextDay(){long uid=user(),id=record();assertThat(award(uid,id).sheepEarned()).isEqualTo(1);assertThat(award(uid,id).sheepEarned()).isZero();clock.current.set(BASE.plusSeconds(86400));assertThat(award(uid,id).sheepEarned()).isZero();assertThat(award(uid,record()).sheepEarned()).isEqualTo(1);assertThat(sheep.balance(uid).balance()).isEqualTo(2);}
    @Test void zeroRewardDecisionCannotBeRewardedByNextDayRetry(){long uid=user();award(uid,record());long zeroRecord=record();assertThat(award(uid,zeroRecord).sheepEarned()).isZero();clock.current.set(BASE.plusSeconds(86400));assertThat(award(uid,zeroRecord).sheepEarned()).isZero();assertThat(award(uid,record()).sheepEarned()).isEqualTo(1);}
    @Test void seoulMidnightResetsDailyReward(){long uid=user();clock.current.set(Instant.parse("2026-11-17T14:59:59Z"));assertThat(award(uid,record()).sheepEarned()).isEqualTo(1);clock.current.set(Instant.parse("2026-11-17T15:00:00Z"));assertThat(award(uid,record()).sheepEarned()).isEqualTo(1);}
    @Test void standaloneAwardWithoutRecordSaveTransactionIsRejected(){long uid=user();assertThatThrownBy(()->sheep.awardRecord(uid,record())).isInstanceOf(IllegalTransactionStateException.class);assertThat(wallets.existsById(uid)).isFalse();}
    @Test void originalRecordTransactionFailureRollsBackWalletLedgerAndDecision(){long uid=user(),rid=record();assertThatThrownBy(()->new TransactionTemplate(transactionManager).execute(s->{sheep.awardRecord(uid,rid);throw new IllegalStateException("record failed");})).isInstanceOf(IllegalStateException.class);assertThat(wallets.existsById(uid)).isFalse();assertThat(rewards.findById(rid)).isEmpty();assertThat(sheep.transactions(uid,null,20).items()).isEmpty();assertThat(award(uid,rid).sheepEarned()).isEqualTo(1);}
    @Test void zeroRewardDecisionAlsoRollsBackOnSaveFailure(){long uid=user(),rid=record();award(uid,record());assertThatThrownBy(()->new TransactionTemplate(transactionManager).execute(s->{sheep.awardRecord(uid,rid);throw new IllegalStateException("record failed");})).isInstanceOf(IllegalStateException.class);assertThat(rewards.findById(rid)).isEmpty();}
    @Test void parallelFirstAwardsSafelyCreateOneWalletAndOneDailyCredit() throws Exception {long uid=user();var values=parallel(8,()->award(uid,record()));assertThat(values.stream().mapToInt(SheepAwardResponse::sheepEarned).sum()).isEqualTo(1);assertThat(sheep.balance(uid).balance()).isEqualTo(1);assertThat(sheep.transactions(uid,null,20).items()).hasSize(1);}
    @Test void parallelRetriesOfSameRecordAreIdempotent() throws Exception {long uid=user(),rid=record();var values=parallel(8,()->award(uid,rid));assertThat(values.stream().mapToInt(SheepAwardResponse::sheepEarned).sum()).isEqualTo(1);assertThat(sheep.balance(uid).balance()).isEqualTo(1);}
    @Test void cannotReuseAnotherUsersRecordDecision(){long owner=user(),other=user(),rid=record();award(owner,rid);code(()->award(other,rid),ErrorCode.FORBIDDEN);assertThat(sheep.balance(other).balance()).isZero();}
    @Test void stringQuestionReferenceIsPreservedAndDeduplicated(){long uid=user();fund(uid,2);String ref="conversation:12:question:6";assertThat(sheep.spendExtraChat(uid,ref).applied()).isTrue();assertThat(sheep.spendExtraChat(uid,ref).applied()).isFalse();assertThat(sheep.spendExtraChat(uid,"conversation:13:question:6").applied()).isTrue();assertThat(sheep.balance(uid).balance()).isZero();assertThat(sheep.transactions(uid,null,20).items().get(1).referenceId()).isEqualTo(ref);}
    @Test void insufficientFundsCannotCreateDebitOrNegativeWallet(){long uid=user();code(()->sheep.spendExtraChat(uid,"conversation:1:question:6"),ErrorCode.NOT_ENOUGH_WOOL);assertThat(sheep.balance(uid).balance()).isZero();assertThat(sheep.transactions(uid,null,20).items()).isEmpty();}
    @Test void parallelDebitsNeverOverspend() throws Exception {long uid=user();fund(uid,1);AtomicLong requests=new AtomicLong();var values=parallel(8,()->{try{sheep.spendExtraChat(uid,"conversation:22:question:"+requests.incrementAndGet());return "OK";}catch(BusinessException e){return e.getErrorCode().name();}});assertThat(values.stream().filter("OK"::equals).count()).isEqualTo(1);assertThat(values.stream().filter("NOT_ENOUGH_WOOL"::equals).count()).isEqualTo(7);assertThat(sheep.balance(uid).balance()).isZero();}
    @Test void sameReferenceParallelDebitsOnlyChargeOnce() throws Exception {long uid=user();fund(uid,1);var values=parallel(8,()->sheep.spendExtraChat(uid,"conversation:99:question:6"));assertThat(values.stream().filter(SheepChangeResponse::applied).count()).isEqualTo(1);assertThat(sheep.transactions(uid,null,20).items()).hasSize(2);}
    @Test void failedConversationTransactionRollsBackDebitAndLedger(){long uid=user();fund(uid,1);String ref="conversation:4:question:6";assertThatThrownBy(()->new TransactionTemplate(transactionManager).execute(s->{sheep.spendExtraChat(uid,ref);throw new IllegalStateException("question failed");})).isInstanceOf(IllegalStateException.class);assertThat(sheep.balance(uid).balance()).isEqualTo(1);assertThat(sheep.spendExtraChat(uid,ref).applied()).isTrue();}
    @Test void refundNeedsActualMeetingDebit(){long uid=user();code(()->sheep.refundMeeting(uid,201),ErrorCode.REFUND_NOT_ELIGIBLE);assertThat(sheep.balance(uid).balance()).isZero();}
    @Test void meetingCreationAndJoinAreDistinctCharges(){long uid=user();fund(uid,2);assertThat(sheep.useMeetingCreate(uid,201).applied()).isTrue();assertThat(sheep.useMeetingJoin(uid,201).applied()).isTrue();assertThat(sheep.useMeetingJoin(uid,201).applied()).isFalse();assertThat(sheep.balance(uid).balance()).isZero();}
    @Test void concurrentMeetingRefundsAddExactlyOnePositiveEntry() throws Exception {long uid=user();fund(uid,1);sheep.useMeetingJoin(uid,201);var values=parallel(8,()->sheep.refundMeeting(uid,201));assertThat(values.stream().filter(SheepChangeResponse::applied).count()).isEqualTo(1);var items=sheep.transactions(uid,null,20).items();assertThat(items).hasSize(3);assertThat(items.get(0).type()).isEqualTo(SheepTransactionType.MEETING_REFUND);assertThat(items.get(0).amount()).isEqualTo(1);assertThat(items.get(0).meetingId()).isEqualTo(201L);assertThat(items.get(2).meetingId()).isNull();}
    @Test void invalidKeysAndIdsAreRejected(){long uid=user();for(String ref:new String[]{""," "," key","key ","a".repeat(161)})code(()->sheep.spendExtraChat(uid,ref),ErrorCode.INVALID_REQUEST);code(()->sheep.useMeetingJoin(uid,0),ErrorCode.INVALID_REQUEST);code(()->award(uid,0),ErrorCode.INVALID_REQUEST);}
    @Test void paginationAndOwnerIsolation(){long uid=user(),other=user();fund(uid,3);var first=sheep.transactions(uid,null,1);assertThat(first.hasNext()).isTrue();assertThat(first.items()).hasSize(1);var rest=sheep.transactions(uid,first.nextCursor(),20);assertThat(rest.items()).hasSize(2);assertThat(rest.items()).noneMatch(x->x.transactionId()==first.items().get(0).transactionId());assertThat(sheep.transactions(other,null,20).items()).isEmpty();}
    @Test void staleManagedWalletIsRefreshedAfterAnotherTransactionSpends() throws Exception {
        long uid=user();fund(uid,1);var loaded=new CountDownLatch(1);var spent=new CountDownLatch(1);var pool=Executors.newSingleThreadExecutor();
        try{
            var attempt=pool.submit(()->{code(()->new TransactionTemplate(transactionManager).execute(s->{wallets.findById(uid).orElseThrow();loaded.countDown();try{if(!spent.await(15,TimeUnit.SECONDS))throw new IllegalStateException("timeout");}catch(InterruptedException e){throw new RuntimeException(e);}sheep.spendExtraChat(uid,"conversation:stale:question:7");return null;}),ErrorCode.NOT_ENOUGH_WOOL);return true;});
            assertThat(loaded.await(15,TimeUnit.SECONDS)).isTrue();sheep.spendExtraChat(uid,"conversation:fresh:question:6");spent.countDown();assertThat(attempt.get(20,TimeUnit.SECONDS)).isTrue();assertThat(sheep.balance(uid).balance()).isZero();
        }finally{spent.countDown();pool.shutdownNow();}
    }
    @Test void opaqueReferenceKeysRemainCaseSensitive(){long uid=user();fund(uid,2);assertThat(sheep.spendExtraChat(uid,"conversation:ABC:question:6").applied()).isTrue();assertThat(sheep.spendExtraChat(uid,"conversation:abc:question:6").applied()).isTrue();assertThat(sheep.balance(uid).balance()).isZero();}
    private <T> List<T> parallel(int n,Callable<T> task) throws Exception {var pool=Executors.newFixedThreadPool(n);var gate=new CountDownLatch(1);try{List<Future<T>> jobs=new ArrayList<>();for(int i=0;i<n;i++)jobs.add(pool.submit(()->{gate.await();return task.call();}));gate.countDown();List<T> values=new ArrayList<>();for(var job:jobs)values.add(job.get(30,TimeUnit.SECONDS));return values;}finally{pool.shutdownNow();}}
    @TestConfiguration static class Fixtures {@Bean @Primary TestClock clock(){return new TestClock();}}
    static class TestClock extends Clock {final AtomicReference<Instant> current=new AtomicReference<>(BASE);public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId zone){return Clock.fixed(instant(),zone);}public Instant instant(){return current.get();}}
}
