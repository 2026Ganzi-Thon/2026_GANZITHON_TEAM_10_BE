package com.ganzithon.moyang.user.domain;

import com.ganzithon.moyang.topic.domain.ThemeCode;
import java.util.List;
import java.util.Set;

/**
 * 온보딩 선택지 코드표. 노션 「온보딩 선택지 목록」과 같은 값이다.
 * 고민(themes)은 topic 패키지의 ThemeCode를 그대로 쓴다. 문구(label)는 선택지 목록 API가 쓸 수 있게 함께 둔다.
 */
public final class OnboardingOptions {
    private OnboardingOptions() {}

    public record Option(String code, String label) {}

    /** '직접 말할래요' 감정 코드. 고르면 emotionText가 필요하다. */
    public static final String EMOTION_DIRECT = "DIRECT";
    /** '기타(직접 입력)' 생활환경 코드. 고르면 lifeStatusText가 필요하다. */
    public static final String LIFE_STATUS_ETC = "ETC";

    public static final List<Option> THEMES = List.of(
            new Option("STUDY_CAREER", "학업·진로·일"),
            new Option("LOVE", "연애·이별"),
            new Option("MONEY", "경제·금전"),
            new Option("DAILY_FUTURE", "일상·미래"),
            new Option("SELF_HEALTH", "나 자신·건강"),
            new Option("RELATION_FAMILY", "인간관계·가족"),
            new Option("UNSURE", "잘 모르겠어요"),
            new Option("DIRECT", "기타 (직접 입력)"));

    public static final List<Option> EMOTIONS = List.of(
            new Option("ANXIOUS", "불안해요"),
            new Option("UNSURE", "잘 모르겠어요"),
            new Option("LONELY", "외로워요"),
            new Option("JOY", "기뻐요"),
            new Option("TIRED", "무기력해요"),
            new Option("ANNOYED", "짜증나요"),
            new Option("STUCK", "답답해요"),
            new Option("COMPLEX", "복잡한 감정"),
            new Option("CALM", "편안해요"),
            new Option("DIRECT", "직접 말할래요"));

    public static final List<Option> LIFE_STATUSES = List.of(
            new Option("STUDENT", "대학생"),
            new Option("GRAD_STUDENT", "대학원생"),
            new Option("CAREGIVER", "육아·돌봄"),
            new Option("FREELANCER", "프리랜서·자영업자"),
            new Option("JOB_SEEKER", "취업준비생"),
            new Option("WORKER", "직장인"),
            new Option("ON_LEAVE", "휴직·휴식"),
            new Option("ETC", "기타 (직접 입력)"));

    public static final List<Option> ART_EXPERIENCES = List.of(
            new Option("NONE", "경험 없음"),
            new Option("LITTLE", "경험 적음"),
            new Option("OCCASIONAL", "가끔 활동"),
            new Option("REGULAR", "꾸준한 활동"),
            new Option("PRO", "전문 경험"));

    private static final Set<String> EMOTION_CODES = codes(EMOTIONS);
    private static final Set<String> LIFE_STATUS_CODES = codes(LIFE_STATUSES);
    private static final Set<String> ART_EXPERIENCE_CODES = codes(ART_EXPERIENCES);

    /** 고민 코드는 주제 추천이 쓰는 ThemeCode와 항상 같은 기준으로 판단한다. */
    public static boolean isTheme(String code) { return ThemeCode.of(code).isPresent(); }
    public static boolean isEmotion(String code) { return EMOTION_CODES.contains(code); }
    public static boolean isLifeStatus(String code) { return LIFE_STATUS_CODES.contains(code); }
    public static boolean isArtExperience(String code) { return ART_EXPERIENCE_CODES.contains(code); }

    private static Set<String> codes(List<Option> options) {
        return options.stream().map(Option::code).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
}
