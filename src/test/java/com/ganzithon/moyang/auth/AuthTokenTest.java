package com.ganzithon.moyang.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ganzithon.moyang.common.exception.BusinessException;
import com.ganzithon.moyang.user.domain.User;
import com.ganzithon.moyang.user.repository.UserRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/** 토큰 발급·검증, 진짜 CurrentUserProvider(Authorization 헤더), 운영 fail-fast 설정 검증. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:auth-token;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "moyang.upload-dir=build/test-uploads",
        "moyang.jwt.secret=test-only-secret-key-0123456789-abcdef"
})
class AuthTokenTest {

    static final String SECRET = "test-only-secret-key-0123456789-abcdef";

    @Autowired WebApplicationContext context;
    @Autowired JwtTokenProvider tokens;
    @Autowired UserRepository users;

    MockMvc mvc() {
        return MockMvcBuilders.webAppContextSetup(context).build();
    }

    long newUser(String loginId) {
        return users.save(new User(loginId, "x", "닉네임")).getId();
    }

    @Test
    void issuedTokenRoundTrips() {
        assertThat(tokens.parseUserId(tokens.issue(42))).isEqualTo(42);
    }

    @Test
    void tamperedAndGarbageTokensAreUnauthorized() {
        String token = tokens.issue(42);
        String tampered = token.substring(0, token.length() - 2) + (token.endsWith("AA") ? "BB" : "AA");
        assertThatThrownBy(() -> tokens.parseUserId(tampered)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> tokens.parseUserId("not-a-jwt")).isInstanceOf(BusinessException.class);
    }

    @Test
    void expiredAndOtherKeyTokensAreUnauthorized() {
        var key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        String expired = Jwts.builder().subject("1")
                .issuedAt(new Date(System.currentTimeMillis() - 100_000))
                .expiration(new Date(System.currentTimeMillis() - 50_000)).signWith(key).compact();
        var otherKey = Keys.hmacShaKeyFor("another-secret-key-0123456789-abcdefgh".getBytes(StandardCharsets.UTF_8));
        String foreign = Jwts.builder().subject("1")
                .expiration(new Date(System.currentTimeMillis() + 100_000)).signWith(otherKey).compact();
        assertThatThrownBy(() -> tokens.parseUserId(expired)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> tokens.parseUserId(foreign)).isInstanceOf(BusinessException.class);
    }

    @Test
    void shortSecretFailsToStart() {
        assertThatThrownBy(() -> new JwtTokenProvider("too-short")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void protectedApiNeedsValidBearerToken() throws Exception {
        long uid = newUser("token-user-1");
        // /api/sheep은 CurrentUserProvider를 쓰는 API (잔액 없으면 0)
        mvc().perform(get("/api/sheep").header("Authorization", "Bearer " + tokens.issue(uid)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        mvc().perform(get("/api/sheep")).andExpect(status().isUnauthorized());
        mvc().perform(get("/api/sheep").header("Authorization", "Bearer garbage")).andExpect(status().isUnauthorized());
        mvc().perform(get("/api/sheep").header("Authorization", tokens.issue(uid))).andExpect(status().isUnauthorized());
        mvc().perform(get("/api/sheep").header("X-User-Id", String.valueOf(uid))).andExpect(status().isUnauthorized());
    }

    @Test
    void tokenOfDeletedUserIsUnauthorized() throws Exception {
        long uid = newUser("token-user-2");
        String token = tokens.issue(uid);
        users.deleteById(uid);
        mvc().perform(get("/api/sheep").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void prodProfileHasNoDefaultForJwtSecret() {
        var yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application-prod.yml"));
        // 기본값(:)이 없는 ${JWT_SECRET}이므로 환경변수가 없으면 기동할 때 값을 못 풀어 실패한다
        assertThat(yaml.getObject().getProperty("moyang.jwt.secret")).isEqualTo("${JWT_SECRET}");
    }
}
