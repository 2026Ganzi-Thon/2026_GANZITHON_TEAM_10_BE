package com.ganzithon.moyang.topic.dto;

import java.util.List;

public final class TopicDtos {
    private TopicDtos() {}

    public record TopicItem(long topicId, String title, String description, List<String> hints) {}

    public record RecommendedTopicsResponse(String themeCode, List<TopicItem> topics) {}
}
