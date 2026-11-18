package com.ganzithon.moyang.auth;

import com.ganzithon.moyang.common.exception.BusinessException;
import com.ganzithon.moyang.common.exception.ErrorCode;
import com.ganzithon.moyang.common.port.Ports.CurrentUserProvider;
import com.ganzithon.moyang.user.repository.UserRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 진짜 로그인 구현. 요청의 Authorization: Bearer {accessToken}을 검증해 userId를 돌려준다.
 * 헤더가 없거나, 토큰이 틀리거나 만료됐거나, 이미 없는 사용자의 토큰이면 UNAUTHORIZED.
 * 별도 필터 없이 이 빈을 쓰는 API(currentUserId() 호출)에서만 인증이 걸린다.
 */
@Component
public class JwtCurrentUserProvider implements CurrentUserProvider {

    private static final String PREFIX = "Bearer ";

    private final JwtTokenProvider tokens;
    private final UserRepository users;

    public JwtCurrentUserProvider(JwtTokenProvider tokens, UserRepository users) {
        this.tokens = tokens;
        this.users = users;
    }

    @Override
    public long currentUserId() {
        var attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        String header = attrs == null ? null : attrs.getRequest().getHeader("Authorization");
        if (header == null || !header.startsWith(PREFIX)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        long userId = tokens.parseUserId(header.substring(PREFIX.length()).trim());
        if (!users.existsById(userId)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return userId;
    }
}
