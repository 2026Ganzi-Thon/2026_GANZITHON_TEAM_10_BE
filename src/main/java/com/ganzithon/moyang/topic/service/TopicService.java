package com.ganzithon.moyang.topic.service;

import com.ganzithon.moyang.common.exception.BusinessException;
import com.ganzithon.moyang.common.exception.ErrorCode;
import com.ganzithon.moyang.common.port.Ports.TopicInfo;
import com.ganzithon.moyang.common.port.Ports.TopicReader;
import com.ganzithon.moyang.topic.domain.ThemeCode;
import com.ganzithon.moyang.topic.dto.TopicDtos.RecommendedTopicsResponse;
import com.ganzithon.moyang.topic.dto.TopicDtos.TopicItem;
import com.ganzithon.moyang.topic.repository.TopicRepository;
import com.ganzithon.moyang.user.repository.UserRepository;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 추천 주제 조회(API)와 TopicReader(다른 도메인이 주제 정보를 읽는 포트)의 진짜 구현. */
@Service
@Transactional(readOnly = true)
public class TopicService implements TopicReader {

    private final TopicRepository topics;
    private final UserRepository users;

    public TopicService(TopicRepository topics, UserRepository users) {
        this.topics = topics;
        this.users = users;
    }

    /** themeCode가 없으면 내 고민(온보딩에서 저장한 값) 기준. 아직 없으면 ONBOARDING_NOT_COMPLETED. */
    public RecommendedTopicsResponse recommended(long userId, String themeCodeParam) {
        String raw = themeCodeParam;
        if (raw == null || raw.isBlank()) {
            raw = users.findById(userId).map(u -> u.getThemeCode()).orElse(null);
            if (raw == null) {
                throw new BusinessException(ErrorCode.ONBOARDING_NOT_COMPLETED);
            }
        }
        ThemeCode theme = ThemeCode.of(raw).orElseThrow(() -> new BusinessException(ErrorCode.INVALID_OPTION_CODE));
        var items = topics.findByThemeCodeOrderByIdAsc(theme.topicGroup().name()).stream()
                .map(t -> new TopicItem(t.getId(), t.getTitle(), t.getDescription(), t.getHints()))
                .toList();
        return new RecommendedTopicsResponse(theme.name(), items);
    }

    @Override
    public Optional<TopicInfo> find(long topicId) {
        return topics.findById(topicId).map(t -> new TopicInfo(t.getId(), t.getTitle(), t.getDescription()));
    }
}
