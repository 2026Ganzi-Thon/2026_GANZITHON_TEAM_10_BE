package com.ganzithon.moyang.auth;

import com.ganzithon.moyang.auth.dto.AuthDtos.LoginRequest;
import com.ganzithon.moyang.auth.dto.AuthDtos.LoginResponse;
import com.ganzithon.moyang.auth.dto.AuthDtos.UserSummary;
import com.ganzithon.moyang.common.exception.BusinessException;
import com.ganzithon.moyang.common.exception.ErrorCode;
import com.ganzithon.moyang.user.domain.User;
import com.ganzithon.moyang.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    // 없는 아이디여도 같은 시간이 걸리도록 비교에 쓰는 가짜 해시 (응답 시간으로 아이디 존재 여부를 알아내지 못하게)
    private final String dummyHash;

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtTokenProvider tokens;

    public AuthService(UserRepository users, PasswordEncoder encoder, JwtTokenProvider tokens) {
        this.users = users;
        this.encoder = encoder;
        this.tokens = tokens;
        this.dummyHash = encoder.encode("dummy-" + System.nanoTime());
    }

    public LoginResponse login(LoginRequest req) {
        User user = users.findByLoginId(req.loginId()).orElse(null);
        boolean ok = encoder.matches(req.password(), user == null ? dummyHash : user.getPasswordHash());
        if (user == null || !ok) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
        return new LoginResponse(tokens.issue(user.getId()), "Bearer", JwtTokenProvider.EXPIRES_IN_SECONDS,
                new UserSummary(user.getId(), user.getNickname(), user.isOnboardingCompleted()));
    }
}
