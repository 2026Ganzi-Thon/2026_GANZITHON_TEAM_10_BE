package com.ganzithon.moyang.record;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import java.net.URI;
import java.net.http.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.springframework.beans.factory.annotation.Autowired;
import com.ganzithon.moyang.record.service.RecordService;
import com.ganzithon.moyang.common.exception.BusinessException;
import com.ganzithon.moyang.common.exception.ErrorCode;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties="spring.datasource.url=jdbc:h2:mem:record-noauth;MODE=MySQL;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
class RecordMissingAuthTest {
    @LocalServerPort int port;
    @Autowired RecordService records;
    @Test void absentRealSnapshotProviderFailsExplicitly() {
        long id=records.create(100);
        records.cardReady(id);
        assertThatThrownBy(()->records.complete(100,id,"제목")).isInstanceOfSatisfying(BusinessException.class,
            e->assertThat(e.getErrorCode()).isEqualTo(ErrorCode.RECORD_SOURCE_UNAVAILABLE));
    }
    @Test void noUserProviderRejectsRecordsAccess() throws Exception {
        var response=HttpClient.newHttpClient().send(HttpRequest.newBuilder(
            URI.create("http://localhost:"+port+"/api/records")).GET().build(),HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.body()).contains("UNAUTHORIZED");
    }
}
