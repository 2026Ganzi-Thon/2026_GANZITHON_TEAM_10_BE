package com.ganzithon.moyang.user.service;

import com.ganzithon.moyang.common.exception.BusinessException;
import com.ganzithon.moyang.common.exception.ErrorCode;
import com.ganzithon.moyang.user.domain.User;
import com.ganzithon.moyang.user.dto.OnboardingDtos.OnboardingData;
import com.ganzithon.moyang.user.dto.UserDtos.MeResponse;
import com.ganzithon.moyang.user.repository.UserOnboardingRepository;
import com.ganzithon.moyang.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 내 정보 조회. 닉네임 · 양 색상 수정, 탈퇴는 이어지는 단위에서 이 서비스에 붙인다. */
@Service
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository users;
    private final UserOnboardingRepository onboardings;

    public UserService(UserRepository users, UserOnboardingRepository onboardings) {
        this.users = users;
        this.onboardings = onboardings;
    }

    public MeResponse me(long userId) {
        User user = users.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        OnboardingData onboarding = onboardings.findById(userId).map(OnboardingData::from).orElse(null);
        return new MeResponse(user.getId(), user.getNickname(), user.isOnboardingCompleted(),
                user.getSheepColor(), onboarding);
    }
}
