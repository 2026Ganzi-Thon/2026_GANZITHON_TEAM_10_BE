package com.ganzithon.moyang.record;

import com.ganzithon.moyang.common.exception.*;
import com.ganzithon.moyang.common.port.Ports.*;
import com.ganzithon.moyang.record.port.RecordSnapshotReader;
import com.ganzithon.moyang.record.dto.RecordDtos.*;
import com.ganzithon.moyang.record.service.RecordService;
import com.ganzithon.moyang.sheep.service.SheepService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.context.request.*;
import tools.jackson.databind.json.JsonMapper;
import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
 properties="spring.datasource.url=jdbc:h2:mem:record-flow;MODE=MySQL;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test") @Import(RecordFlowTest.Config.class)
class RecordFlowTest {
 @Autowired RecordService records; @Autowired RecordPort port;
 @Autowired SheepService sheep; @Autowired PlatformTransactionManager manager;
 @Autowired JdbcTemplate jdbc;
 @Autowired com.ganzithon.moyang.record.repository.RecordRepository repository;
 @LocalServerPort int server;
 static final AtomicLong IDS=new AtomicLong(100000);
 static final Map<Long,Snapshot> SOURCES=new ConcurrentHashMap<>();
 static Snapshot snapshot(long id,long uid){return new Snapshot(id,uid,true,"AI가 만든 회고 제목","/api/drawings/1/image","description",
   new Analysis("tree","center","soft lines","calm"),List.of(new Message(1,"ASSISTANT","question",OffsetDateTime.now()),new Message(2,"USER","answer",OffsetDateTime.now())));}
 long ready(long uid){long id=port.create(uid);SOURCES.put(id,snapshot(id,uid));port.cardReady(id);return id;}
 HttpResponse<String> request(String method,String path,String body,Long uid)throws Exception{
  var b=HttpRequest.newBuilder(URI.create("http://localhost:"+server+"/api/records"+path)).header("Content-Type","application/json");
  if(uid!=null)b.header("X-Test-User",uid.toString());
  return HttpClient.newHttpClient().send(b.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
 }
 @Test void completionFreezesSnapshotAndAwardsOnlyFirstDailySave(){long uid=IDS.incrementAndGet(),a=ready(uid),b=ready(uid);
  assertThat(records.complete(uid,a).sheepEarned()).isEqualTo(1);
  assertThat(records.complete(uid,b).sheepEarned()).isZero();assertThat(records.complete(uid,a).sheepEarned()).isZero();
  SOURCES.remove(a);var d=records.detail(uid,a);assertThat(d.title()).isEqualTo("AI가 만든 회고 제목");assertThat(records.list(uid,null,null,20).items()).allSatisfy(item->assertThat(item.title()).isEqualTo("AI가 만든 회고 제목"));assertThat(d.analysis().mainElements()).isEqualTo("tree");assertThat(d.messages()).hasSize(2);assertThat(d.messages().get(0).role()).isEqualTo("ASSISTANT");assertThat(d.completedAt().getOffset()).isEqualTo(ZoneOffset.ofHours(9));
  assertThat(sheep.balance(uid).balance()).isEqualTo(1);
 }
 @Test void draftAndUnreadyDataCannotBeCompleted(){long uid=IDS.incrementAndGet(),id=port.create(uid);
  assertThatThrownBy(()->records.complete(uid,id)).isInstanceOf(BusinessException.class);port.cardReady(id);
  assertThatThrownBy(()->records.complete(uid,id)).isInstanceOf(BusinessException.class);assertThat(sheep.balance(uid).balance()).isZero();
  SOURCES.put(id,new Snapshot(id,uid,true,"title","url",null,null,List.of()));
  assertThatThrownBy(()->records.complete(uid,id)).isInstanceOf(BusinessException.class);assertThat(records.today(uid).completed()).isFalse();
 }
 @Test void ownershipAndDeletedAccessAreRejected(){long uid=IDS.incrementAndGet(),id=ready(uid);records.complete(uid,id);
  assertThatThrownBy(()->records.detail(uid+1,id)).isInstanceOf(BusinessException.class);
  assertThatThrownBy(()->records.complete(uid+1,id)).isInstanceOf(BusinessException.class);
  assertThatThrownBy(()->records.delete(uid+1,id)).isInstanceOf(BusinessException.class);
  records.delete(uid,id);port.cardReady(id);assertThatThrownBy(()->records.complete(uid,id)).isInstanceOf(BusinessException.class);
  assertThatThrownBy(()->records.detail(uid,id)).isInstanceOf(BusinessException.class);assertThat(sheep.balance(uid).balance()).isEqualTo(1);
 }
 @Test void deletionRevealsPreviousCalendarRecordWithoutRewardReset(){long uid=IDS.incrementAndGet(),a=ready(uid),b=ready(uid);records.complete(uid,a);records.complete(uid,b);
  YearMonth month=YearMonth.now(ZoneId.of("Asia/Seoul"));assertThat(records.calendar(uid,month).items().get(0).recordId()).isEqualTo(b);
  records.delete(uid,b);assertThat(records.calendar(uid,month).items().get(0).recordId()).isEqualTo(a);
  records.delete(uid,a);assertThat(records.today(uid).completed()).isFalse();assertThat(records.complete(uid,ready(uid)).sheepEarned()).isZero();
 }
 @Test void listFiltersDraftsAndUsesStableCompletionCursor(){long uid=IDS.incrementAndGet();port.create(uid);long a=ready(uid),b=ready(uid);records.complete(uid,b);records.complete(uid,a);
  var first=records.list(uid,null,null,1);assertThat(first.items().get(0).recordId()).isEqualTo(a);assertThat(first.hasNext()).isTrue();
  assertThat(records.list(uid,null,first.nextCursor(),1).items().get(0).recordId()).isEqualTo(b);
  assertThat(records.list(uid,LocalDate.now().minusYears(1),null,20).items()).isEmpty();
  assertThat(records.list(uid+1,null,null,20).items()).isEmpty();
 }
 @Test void completionRollsBackTogetherWithWallet(){long uid=IDS.incrementAndGet(),id=ready(uid);
  assertThatThrownBy(()->new TransactionTemplate(manager).execute(s->{records.complete(uid,id);throw new IllegalStateException();})).isInstanceOf(IllegalStateException.class);
  assertThat(sheep.balance(uid).balance()).isZero();assertThat(records.today(uid).completed()).isFalse();assertThat(records.complete(uid,id).sheepEarned()).isEqualTo(1);
 }
 @Test void parallelCompletionOfSameRecordPaysOnce() throws Exception {long uid=IDS.incrementAndGet(),id=ready(uid);var pool=Executors.newFixedThreadPool(2);
  try{var futures=pool.invokeAll(List.of(()->records.complete(uid,id).sheepEarned(),()->records.complete(uid,id).sheepEarned()));int sum=0;for(var f:futures)sum+=(Integer)f.get();assertThat(sum).isEqualTo(1);}finally{pool.shutdownNow();}
 }
 @Test void httpEndpointsReturnExpectedFieldsAndErrors()throws Exception{long uid=IDS.incrementAndGet(),id=ready(uid);
  var save=request("POST","","{\"recordId\":"+id+"}",uid);assertThat(save.statusCode()).isEqualTo(201);assertThat(save.body()).contains("sheepEarned","sheepBalance");
  var detail=request("GET","/"+id,null,uid);assertThat(detail.statusCode()).isEqualTo(200);assertThat(detail.body()).contains("mainElements","spatialComposition","lineExpression","emotionMeaning","sequenceNo","sentAt","AI가 만든 회고 제목");
  for(String path:List.of("?size=0","?size=bad","?cursor=bad","?cursor=-1","?date=bad","/calendar?month=bad")){assertThat(request("GET",path,null,uid).statusCode()).as(path).isEqualTo(400);}
  assertThat(request("GET","",null,null).statusCode()).isEqualTo(401);
  assertThat(request("POST","","{}",uid).statusCode()).isEqualTo(400);
  assertThat(request("DELETE","/"+id,null,uid).statusCode()).isEqualTo(204);
  assertThat(request("GET","/"+id,null,uid).statusCode()).isEqualTo(404);
 }
 @Test void equalCompletionTimesHaveStableCursorAndDeletedAnchorStillWorks(){long uid=IDS.incrementAndGet(),a=ready(uid),b=ready(uid);records.complete(uid,a);records.complete(uid,b);
  jdbc.update("update records set completed_at=? where user_id=?",LocalDateTime.of(2026,11,4,12,0),uid);
  var first=records.list(uid,null,null,1);assertThat(first.items().get(0).recordId()).isEqualTo(b);records.delete(uid,b);
  assertThat(records.list(uid,null,first.nextCursor(),1).items().get(0).recordId()).isEqualTo(a);
  assertThatThrownBy(()->records.list(uid+1,null,first.nextCursor(),20)).isInstanceOf(BusinessException.class);
 }
 @Test void calendarFiltersMonthAndSelectsCompletionRatherThanCreationId(){long uid=IDS.incrementAndGet(),a=ready(uid),b=ready(uid);records.complete(uid,a);records.complete(uid,b);
  jdbc.update("update records set record_date=?,completed_at=? where id=?",LocalDate.of(2026,10,31),LocalDateTime.of(2026,10,31,23,59),b);
  jdbc.update("update records set record_date=?,completed_at=? where id=?",LocalDate.of(2026,11,1),LocalDateTime.of(2026,11,1,0,1),a);
  assertThat(records.calendar(uid,YearMonth.of(2026,11)).items()).extracting(CalendarItem::recordId).containsExactly(a);
  assertThat(records.list(uid,LocalDate.of(2026,10,31),null,20).items()).extracting(ListItem::recordId).containsExactly(b);
 }
 @Test void mismatchedOwnerAndMalformedMessagesDoNotPay(){long uid=IDS.incrementAndGet(),id=ready(uid);
  SOURCES.put(id,snapshot(id,uid+1));assertThatThrownBy(()->records.complete(uid,id)).isInstanceOf(BusinessException.class);
  var original=snapshot(id,uid);SOURCES.put(id,new Snapshot(id,uid,true,original.title(),original.imageUrl(),null,original.analysis(),List.of(new Message(2,"USER","answer",OffsetDateTime.now()))));
  assertThatThrownBy(()->records.complete(uid,id)).isInstanceOf(BusinessException.class);assertThat(sheep.balance(uid).balance()).isZero();
 }
 @Test void recordPortParticipatesInUploadRollback(){long uid=IDS.incrementAndGet();long[] id={0};
  assertThatThrownBy(()->new TransactionTemplate(manager).execute(s->{id[0]=port.create(uid);throw new IllegalStateException();})).isInstanceOf(IllegalStateException.class);
  assertThatThrownBy(()->port.cardReady(id[0])).isInstanceOf(BusinessException.class);
 }
 @Test void missingAiTitleCannotCompleteOrEarnSheep(){long uid=IDS.incrementAndGet(),id=ready(uid);var source=snapshot(id,uid);
  for(String title:new String[]{null,""," ","a".repeat(501)}){
   SOURCES.put(id,new Snapshot(id,uid,true,title,source.imageUrl(),null,source.analysis(),source.messages()));
   assertThatThrownBy(()->records.complete(uid,id)).isInstanceOfSatisfying(BusinessException.class,
      e->assertThat(e.getErrorCode()).isEqualTo(ErrorCode.RECORD_NOT_READY));
   assertThat(sheep.balance(uid).balance()).isZero();
  }
 }
 @Test void readySignalDoesNotReopenCompletedRecord(){long uid=IDS.incrementAndGet(),id=ready(uid);
  records.complete(uid,id);port.cardReady(id);SOURCES.remove(id);
  assertThat(records.complete(uid,id).sheepEarned()).isZero();
 }
 @Test void stalePersistenceContextCannotOverwriteAnotherCompletion(){long uid=IDS.incrementAndGet(),id=ready(uid);
  var outer=new TransactionTemplate(manager);
  outer.execute(s->{repository.findById(id).orElseThrow();
   var inner=new TransactionTemplate(manager);inner.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
   inner.execute(t->records.complete(uid,id));SOURCES.remove(id);
   assertThat(records.complete(uid,id).sheepEarned()).isZero();return null;
  });
 }
 @TestConfiguration static class Config {
  @Bean RecordSnapshotReader reader(){return id->Optional.ofNullable(SOURCES.get(id));}
  @Bean CurrentUserProvider currentUser(){return ()->{var attrs=(ServletRequestAttributes)RequestContextHolder.getRequestAttributes();String v=attrs==null?null:attrs.getRequest().getHeader("X-Test-User");if(v==null)throw new BusinessException(ErrorCode.UNAUTHORIZED);return Long.parseLong(v);};}
 }
}
