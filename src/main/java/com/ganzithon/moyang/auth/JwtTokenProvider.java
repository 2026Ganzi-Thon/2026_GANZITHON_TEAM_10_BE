package com.ganzithon.moyang.auth;

import com.ganzithon.moyang.common.exception.BusinessException;
import com.ganzithon.moyang.common.exception.ErrorCode;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Date;
import javax.crypto.SecretKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 액세스 토큰(JWT, HS256) 발급·검증.
 * 서명 키는 환경변수 JWT_SECRET(32자 이상)으로만 받는다. 코드에 기본 키는 없다.
 * - 운영(prod): application-prod.yml이 기본값 없이 ${JWT_SECRET}을 쓰므로 없으면 기동 자체가 실패한다.
 * - 그 외(local·테스트): 비어 있으면 실행할 때마다 무작위 키를 만든다. 재시작하면 기존 토큰은 무효가 된다.
 */
@Component
public class JwtTokenProvider {

    public static final long EXPIRES_IN_SECONDS = 24 * 60 * 60;
    private static final int MIN_SECRET_BYTES = 32;
    private static final Logger log = LoggerFactory.getLogger(JwtTokenProvider.class);

    private final SecretKey key;

    public JwtTokenProvider(@Value("${moyang.jwt.secret:}") String secret) {
        if (secret == null || secret.isBlank()) {
            byte[] random = new byte[MIN_SECRET_BYTES];
            new SecureRandom().nextBytes(random);
            this.key = Keys.hmacShaKeyFor(random);
            log.warn("JWT_SECRET이 없어 임시 키로 실행해요. 서버를 다시 켜면 로그인이 풀려요.");
            return;
        }
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("JWT_SECRET은 32바이트(영문 32자) 이상이어야 해요.");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
    }

    /** 토큰을 검증해 userId를 돌려준다. 서명 불일치·만료·형식 오류는 모두 UNAUTHORIZED. */
    public long parseUserId(String token) {
        try {
            String subject = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload().getSubject();
            return Long.parseLong(subject);
        } catch (JwtException | IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
    }

    public String issue(long userId) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(Long.toString(userId))
                .issuedAt(new Date(now))
                .expiration(new Date(now + EXPIRES_IN_SECONDS * 1000))
                .signWith(key)
                .compact();
    }
}
