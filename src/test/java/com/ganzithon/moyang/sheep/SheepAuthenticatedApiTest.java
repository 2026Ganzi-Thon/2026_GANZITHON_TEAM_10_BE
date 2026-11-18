package com.ganzithon.moyang.sheep;
import com.ganzithon.moyang.common.exception.*;
import com.ganzithon.moyang.common.port.Ports.*;
import com.ganzithon.moyang.sheep.adapter.WoolPortAdapter;
import com.ganzithon.moyang.sheep.service.SheepService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.net.URI;
import java.net.http.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.assertj.core.api.Assertions.*;
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties="spring.datasource.url=jdbc:h2:mem:sheep-http;MODE=MySQL;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test") @Import(SheepAuthenticatedApiTest.TestPorts.class)
class SheepAuthenticatedApiTest {
 @LocalServerPort int port;@Autowired SheepService sheep;@Autowired WoolPort wool;@Autowired PlatformTransactionManager manager;
 static final AtomicLong IDS=new AtomicLong(5000),RECORDS=new AtomicLong(8000);
 final JsonMapper json=JsonMapper.builder().build();
 HttpResponse<String> get(String path,Long uid) throws Exception{var b=HttpRequest.newBuilder(URI.create("http://localhost:"+port+path));if(uid!=null)b.header("X-Test-User",uid.toString());return HttpClient.newHttpClient().send(b.GET().build(),HttpResponse.BodyHandlers.ofString());}
 JsonNode data(HttpResponse<String> r){return json.readTree(r.body()).path("data");}
 long funded(){long uid=IDS.incrementAndGet();new TransactionTemplate(manager).execute(s->sheep.awardRecord(uid,RECORDS.incrementAndGet()));return uid;}
 @Test void authenticatedBalanceAndHistoryAreOwnerScoped() throws Exception{long owner=funded(),other=IDS.incrementAndGet();var r=get("/api/sheep",owner);assertThat(r.statusCode()).isEqualTo(200);assertThat(data(r).path("balance").asInt()).isEqualTo(1);assertThat(json.readTree(r.body()).path("success").asBoolean()).isTrue();assertThat(data(get("/api/sheep",other)).path("balance").asInt()).isZero();assertThat(data(get("/api/sheep/transactions",other)).path("items").size()).isZero();}
 @Test void livePortIsChosenOverFallbackAndStringKeyIsReturnedExactly() throws Exception{long uid=funded();assertThat(wool).isInstanceOf(WoolPortAdapter.class);String ref="conversation:ABC:question:6";wool.spend(uid,ref);wool.spend(uid,ref);assertThat(wool.balance(uid)).isZero();var item=data(get("/api/sheep/transactions?size=1",uid)).path("items").get(0);assertThat(item.path("type").asText()).isEqualTo("AI_EXTRA_CHAT");assertThat(item.path("referenceId").asText()).isEqualTo(ref);assertThat(item.path("meetingId").isNull()).isTrue();assertThat(item.path("createdAt").asText()).endsWith("+09:00");}
 @Test void meetingRefundReturnsPositiveAmountAndMeetingId() throws Exception{long uid=funded();sheep.useMeetingCreate(uid,33);sheep.refundMeeting(uid,33);var page=data(get("/api/sheep/transactions?size=1",uid));var item=page.path("items").get(0);assertThat(item.path("amount").asInt()).isEqualTo(1);assertThat(item.path("type").asText()).isEqualTo("MEETING_REFUND");assertThat(item.path("meetingId").asLong()).isEqualTo(33);assertThat(page.path("hasNext").asBoolean()).isTrue();long cursor=page.path("nextCursor").asLong();assertThat(data(get("/api/sheep/transactions?cursor="+cursor,uid)).path("items").size()).isEqualTo(2);}
 @Test void invalidQueryInputsAre400InTeamEnvelope() throws Exception{long uid=IDS.incrementAndGet();for(String query:new String[]{"size=0","size=51","size=bad","cursor=-1","cursor=bad"}){var r=get("/api/sheep/transactions?"+query,uid);assertThat(r.statusCode()).isEqualTo(400);assertThat(r.body()).contains("INVALID_REQUEST","\"success\":false");}}
 @Test void missingTestIdentityIsRejected() throws Exception{assertThat(get("/api/sheep",null).statusCode()).isEqualTo(401);}
 @Test void portSpendingRollsBackWithItsCallerTransaction(){long uid=funded();assertThatThrownBy(()->new TransactionTemplate(manager).execute(s->{wool.spend(uid,"conversation:rollback:question:6");throw new IllegalStateException("conversation failed");})).isInstanceOf(IllegalStateException.class);assertThat(wool.balance(uid)).isEqualTo(1);}
 @TestConfiguration static class TestPorts {
  @Bean @Primary CurrentUserProvider userProvider(){return ()->{var attrs=(ServletRequestAttributes)RequestContextHolder.getRequestAttributes();String value=attrs==null?null:attrs.getRequest().getHeader("X-Test-User");if(value==null)throw new BusinessException(ErrorCode.UNAUTHORIZED);try{return Long.parseLong(value);}catch(NumberFormatException e){throw new BusinessException(ErrorCode.UNAUTHORIZED);}};}
  @Bean @Fallback WoolPort fallbackWool(){return new WoolPort(){public int balance(long uid){throw new AssertionError("fallback selected");}public void spend(long uid,String ref){throw new AssertionError("fallback selected");}};}
 }
}
