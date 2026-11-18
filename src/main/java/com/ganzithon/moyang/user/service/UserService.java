package com.ganzithon.moyang.user.service;

import com.ganzithon.moyang.common.exception.BusinessException;
import com.ganzithon.moyang.common.exception.ErrorCode;
import com.ganzithon.moyang.user.domain.User;
import com.ganzithon.moyang.user.dto.OnboardingDtos.OnboardingData;
import com.ganzithon.moyang.user.dto.UserDtos.MeResponse;
import com.ganzithon.moyang.user.dto.UserDtos.SheepColorRequest;
import com.ganzithon.moyang.user.dto.UserDtos.SheepColorResponse;
import com.ganzithon.moyang.user.repository.UserOnboardingRepository;
import com.ganzithon.moyang.user.repository.UserRepository;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 내 정보 조회와 양 색상 수정. 닉네임 수정, 탈퇴는 이어지는 단위에서 이 서비스에 붙인다. */
@Service
@Transactional(readOnly = true)
public class UserService {

    private static final Pattern SHEEP_COLOR = Pattern.compile("^[A-Z0-9_]{1,20}$");

    private final UserRepository users;
    private final UserOnboardingRepository onboardings;

    public UserService(UserRepository users, UserOnboardingRepository onboardings) {
        this.users = users;
        this.onboardings = onboardings;
    }

    /**
     * 양 프로필 색상 저장. 색 코드값은 프론트가 정하므로 서버는 형식(영문 대문자·숫자·_ 1~20자)만 본다.
     * null · 빈 값은 허용하지 않는다(색을 지우는 화면이 없다). 같은 사용자의 마지막 요청이 이긴다.
     */
    @Transactional
    public SheepColorResponse changeSheepColor(long userId, SheepColorRequest req) {
        String color = req.sheepColor();
        if (color == null || !SHEEP_COLOR.matcher(color).matches()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        User user = users.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        user.changeSheepColor(color);
        return new SheepColorResponse(user.getId(), user.getSheepColor());
    }

    public MeResponse me(long userId) {
        User user = users.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        OnboardingData onboarding = onboardings.findById(userId).map(OnboardingData::from).orElse(null);
        return new MeResponse(user.getId(), user.getNickname(), user.isOnboardingCompleted(),
                user.getSheepColor(), onboarding);
    }
}
