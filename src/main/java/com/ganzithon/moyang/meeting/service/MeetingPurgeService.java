package com.ganzithon.moyang.meeting.service;

import com.ganzithon.moyang.meeting.domain.Meeting;
import com.ganzithon.moyang.meeting.domain.MeetingEnums.MeetingStatus;
import com.ganzithon.moyang.meeting.repository.MeetingMessageRepository;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 모임이 끝나고 24시간이 지나면 채팅 메시지 원문을 DB에서 지운다(개인정보 보호). 조회는 이미 404로 막혀 있지만 데이터가 남지 않게 한다.
 * 모임 · 참여자 · 회고 카드는 남긴다. 몇 번을 불러도 같은 결과이고, 실패하면 다음 회차에 다시 지운다.
 */
@Service
public class MeetingPurgeService {

    private static final Logger log = LoggerFactory.getLogger(MeetingPurgeService.class);

    private final MeetingMessageRepository messages;

    public MeetingPurgeService(MeetingMessageRepository messages) {
        this.messages = messages;
    }

    /** 종료 후 24시간(공개 기간)이 지난 모임의 메시지를 지우고, 지운 개수를 돌려준다. */
    @Transactional
    public int purgeExpired(LocalDateTime now) {
        int deleted = messages.deleteOfMeetingsEndedBefore(MeetingStatus.ENDED, now.minus(Meeting.ENDED_VISIBLE));
        if (deleted > 0) {
            log.info("종료 후 24시간이 지난 모임 채팅 메시지 {}개를 삭제했어요.", deleted);
        }
        return deleted;
    }
}
