package com.ganzithon.moyang.common.port;

import com.ganzithon.moyang.common.exception.BusinessException;
import com.ganzithon.moyang.common.exception.ErrorCode;
import com.ganzithon.moyang.common.port.Ports.CurrentUserProvider;
import com.ganzithon.moyang.common.port.Ports.MeetingReader;
import com.ganzithon.moyang.common.port.Ports.MeetingSharePort;
import com.ganzithon.moyang.common.port.Ports.RecordPort;
import com.ganzithon.moyang.common.port.Ports.TopicReader;
import com.ganzithon.moyang.common.port.Ports.UserContext;
import com.ganzithon.moyang.common.port.Ports.UserContextProvider;
import com.ganzithon.moyang.common.port.Ports.WoolPort;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Fallback;

/**
 * 다른 담당의 진짜 구현이 아직 없을 때 쓰는 "전부 막힘" 기본값. 프로필과 상관없이 등록된다.
 * - 로그인: 항상 401 / 양털: 0개 / 주제 · 모임: 없음 / 온보딩 정보: 비어 있음 / 기록 생성: 실패
 * @Fallback이라 진짜 구현(@Component 등)이나 LocalStubs가 있으면 그쪽이 자동으로 쓰인다.
 * 진짜 구현이 다 올라오면 이 파일은 지워도 된다.
 */
@Configuration
public class PortFallbacks {

    @Bean
    @Fallback
    CurrentUserProvider fallbackCurrentUserProvider() {
        return () -> {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        };
    }

    @Bean
    @Fallback
    UserContextProvider fallbackUserContextProvider() {
        return userId -> UserContext.EMPTY;
    }

    @Bean
    @Fallback
    TopicReader fallbackTopicReader() {
        return topicId -> Optional.empty();
    }

    @Bean
    @Fallback
    WoolPort fallbackWoolPort() {
        return new WoolPort() {
            public int balance(long userId) {
                return 0;
            }

            public void spend(long userId, String referenceId) {
                throw new BusinessException(ErrorCode.NOT_ENOUGH_WOOL);
            }
        };
    }

    @Bean
    @Fallback
    MeetingSharePort fallbackMeetingSharePort() {
        return (drawingId, userId) -> false;
    }

    @Bean
    @Fallback
    MeetingReader fallbackMeetingReader() {
        return meetingId -> Optional.empty();
    }

    /** record 구현 전에는 그림 업로드가 실패한다 (가짜 recordId를 만들면 나중에 회고 저장과 어긋나므로). */
    @Bean
    @Fallback
    RecordPort fallbackRecordPort() {
        return new RecordPort() {
            public long create(long userId) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "기록 기능이 아직 연결되지 않았어요.");
            }

            public void cardReady(long recordId) {
            }
        };
    }
}
