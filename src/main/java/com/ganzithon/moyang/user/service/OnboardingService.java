package com.ganzithon.moyang.user.service;

import com.ganzithon.moyang.common.exception.BusinessException;
import com.ganzithon.moyang.common.exception.ErrorCode;
import com.ganzithon.moyang.user.domain.OnboardingOptions;
import com.ganzithon.moyang.user.domain.User;
import com.ganzithon.moyang.user.domain.UserOnboarding;
import com.ganzithon.moyang.user.dto.OnboardingDtos.OnboardingData;
import com.ganzithon.moyang.user.dto.OnboardingDtos.OnboardingRequest;
import com.ganzithon.moyang.user.dto.OnboardingDtos.OnboardingResponse;
import com.ganzithon.moyang.user.repository.UserOnboardingRepository;
import com.ganzithon.moyang.user.repository.UserRepository;
import java.time.Year;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 온보딩 답 저장. 보낸 값으로 통째로 덮어쓴다. */
@Service
public class OnboardingService {

    static final int MAX_EMOTIONS = 2;
    static final int MAX_TEXT = 100;
    static final int MAX_EXTRA_STORY = 500;
    static final int MIN_BIRTH_YEAR = 1900;
    /** 임시 규칙: 성별 코드값이 확정되기 전까지 영문 대문자와 _ 로 된 20자 이내만 받는다. */
    private static final Pattern GENDER = Pattern.compile("^[A-Z_]{1,20}$");
    private static final String THEME_DIRECT = "DIRECT";

    private final UserRepository users;
    private final UserOnboardingRepository onboardings;
    private final Clock clock;

    public OnboardingService(UserRepository users, UserOnboardingRepository onboardings, Clock clock) {
        this.users = users;
        this.onboardings = onboardings;
        this.clock = clock;
    }

    @Transactional
    public OnboardingResponse save(long userId, OnboardingRequest req) {
        Normalized n = validate(req);

        // 같은 사용자의 동시 저장을 한 줄씩 처리하려고 사용자 행을 먼저 잠근다
        User user = users.findByIdForUpdate(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        UserOnboarding onboarding = onboardings.findById(userId).orElseGet(() -> new UserOnboarding(userId));
        onboarding.overwrite(n.birthYear, n.gender, n.artExperience, n.themeCode,
                n.themeDirect ? n.themeText : null,
                n.lifeStatus, n.lifeEtc ? n.lifeStatusText : null,
                n.emotions, n.emotionDirect ? n.emotionText : null,
                n.extraStory, LocalDateTime.ofInstant(clock.instant(), ZoneId.of("Asia/Seoul")));
        onboardings.save(onboarding);
        user.completeOnboarding(n.themeCode);
        return new OnboardingResponse(true, OnboardingData.from(onboarding));
    }

    /** 검증 순서: 형식·필수·글자 수·개수(INVALID_REQUEST) → 없는 선택지 코드(INVALID_OPTION_CODE). */
    private Normalized validate(OnboardingRequest req) {
        String themeCode = trimToNull(req.themeCode());
        String themeText = trimToNull(req.themeText());
        String lifeStatusText = trimToNull(req.lifeStatusText());
        String emotionText = trimToNull(req.emotionText());
        String extraStory = trimToNull(req.extraStory());
        List<String> emotions = req.emotions() == null ? List.of() : req.emotions();

        if (themeCode == null) {
            throw invalid();
        }
        if (req.birthYear() != null
                && (req.birthYear() < MIN_BIRTH_YEAR || req.birthYear() > Year.now(ZoneId.of("Asia/Seoul")).getValue())) {
            throw invalid();
        }
        if (req.gender() != null && !GENDER.matcher(req.gender()).matches()) {
            throw invalid();
        }
        if (emotions.size() > MAX_EMOTIONS || emotions.stream().anyMatch(e -> e == null || e.isBlank())
                || new HashSet<>(emotions).size() != emotions.size()) {
            throw invalid();
        }
        if (tooLong(themeText, MAX_TEXT) || tooLong(lifeStatusText, MAX_TEXT) || tooLong(emotionText, MAX_TEXT)
                || tooLong(extraStory, MAX_EXTRA_STORY)) {
            throw invalid();
        }

        boolean themeDirect = THEME_DIRECT.equals(themeCode);
        boolean lifeEtc = OnboardingOptions.LIFE_STATUS_ETC.equals(req.lifeStatus());
        boolean emotionDirect = emotions.contains(OnboardingOptions.EMOTION_DIRECT);
        if ((themeDirect && themeText == null) || (lifeEtc && lifeStatusText == null)
                || (emotionDirect && emotionText == null)) {
            throw invalid();
        }

        if (!OnboardingOptions.isTheme(themeCode)
                || (req.artExperience() != null && !OnboardingOptions.isArtExperience(req.artExperience()))
                || (req.lifeStatus() != null && !OnboardingOptions.isLifeStatus(req.lifeStatus()))
                || !emotions.stream().allMatch(OnboardingOptions::isEmotion)) {
            throw new BusinessException(ErrorCode.INVALID_OPTION_CODE);
        }

        return new Normalized(req.birthYear(), req.gender(), req.artExperience(), themeCode, themeText,
                req.lifeStatus(), lifeStatusText, List.copyOf(emotions), emotionText, extraStory,
                themeDirect, lifeEtc, emotionDirect);
    }

    private static BusinessException invalid() {
        return new BusinessException(ErrorCode.INVALID_REQUEST);
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    /** 글자 수는 이모지 같은 문자도 1자로 센다. */
    private static boolean tooLong(String s, int max) {
        return s != null && s.codePointCount(0, s.length()) > max;
    }

    private record Normalized(Integer birthYear, String gender, String artExperience, String themeCode,
                              String themeText, String lifeStatus, String lifeStatusText, List<String> emotions,
                              String emotionText, String extraStory,
                              boolean themeDirect, boolean lifeEtc, boolean emotionDirect) {}
}
