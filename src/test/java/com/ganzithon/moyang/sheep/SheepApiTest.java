package com.ganzithon.moyang.sheep;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import java.net.URI;
import java.net.http.*;
import static org.assertj.core.api.Assertions.assertThat;
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
class SheepApiTest {
 @LocalServerPort int port;
 @Test void walletEndpointRejectsUnauthenticatedRequest(){
  try {
   var request=HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/sheep")).GET().build();
   var response=HttpClient.newHttpClient().send(request,HttpResponse.BodyHandlers.ofString());
   assertThat(response.statusCode()).isEqualTo(401);
   assertThat(response.body()).contains("UNAUTHORIZED");
  } catch(java.io.IOException|InterruptedException e){throw new RuntimeException(e);}
 }
 @Test void userIdHeaderCannotBypassMissingAuthentication() throws Exception {
  var req=HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/sheep")).header("X-User-Id","1").header("X-Test-User","1").GET().build();
  assertThat(HttpClient.newHttpClient().send(req,HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(401);
 }
}
