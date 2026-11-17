package com.ganzithon.moyang.common.port;

import java.util.List;
import java.util.Optional;

/**
 * Drawing · AI 도메인이 다른 담당 도메인에 기대는 지점. 구현은 각 담당이 만든다.
 * (Auth · User · Theme = 백승효, Sheep · Record = 박준석, Meeting = 백승효)
 */
public final class Ports {
    private Ports() {}

    /**
     * 기록(record) = 그림 1장 + 대화 + 회고 카드 묶음. 구현은 record 담당(박준석).
     * 그림 업로드 트랜잭션 안에서 create가 불리고, 그 반환값이 recordId로 그림 · 대화 · 회고 저장에 쓰인다.
     */
    public interface RecordPort {
        /** 기록을 새로 만들고 recordId를 돌려준다 (상태: 진행 중). */
        long create(long userId);

        /** 대화 · 모임이 끝나 회고 카드가 준비됨. 이미 회고 저장(완료)된 기록이면 아무것도 바꾸지 않는다. */
        void cardReady(long recordId);
    }

    /** 로그인한 사용자 ID. 로그인 안 했으면 BusinessException(UNAUTHORIZED)을 던진다. */
    public interface CurrentUserProvider {
        long currentUserId();
    }

    /** 온보딩 답을 사람이 읽는 문구(label)로 바꾼 값. 답하지 않은 항목은 null. */
    public record UserContext(String concern, String feeling, String life, String artExperience,
                              String extraStory) {
        public static final UserContext EMPTY = new UserContext(null, null, null, null, null);
    }

    public interface UserContextProvider {
        UserContext get(long userId);
    }

    public record TopicInfo(long topicId, String title, String description) {}

    public interface TopicReader {
        Optional<TopicInfo> find(long topicId);
    }

    public interface WoolPort {
        int balance(long userId);

        /** 양털 1개 차감. referenceId가 같으면 두 번 차감하지 않는다. 부족하면 BusinessException(NOT_ENOUGH_WOOL). */
        void spend(long userId, String referenceId);
    }

    /** 그림이 사용자가 참여한 모임 채팅에 공유돼 있는지 (채팅 삭제 후에는 false). */
    public interface MeetingSharePort {
        boolean isSharedWith(long drawingId, long userId);
    }

    /** 모임 참여자 한 명: 모임에서 공유한 그림(없으면 null)과 본인이 쓴 텍스트 메시지. */
    public record MeetingMember(long userId, Long drawingId, List<String> messages) {}

    public record MeetingInfo(boolean ended, List<MeetingMember> members) {}

    public interface MeetingReader {
        Optional<MeetingInfo> find(long meetingId);
    }
}

