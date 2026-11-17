package com.ganzithon.moyang.topic.repository;

import com.ganzithon.moyang.topic.domain.Topic;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TopicRepository extends JpaRepository<Topic, Long> {
    List<Topic> findByThemeCodeOrderByIdAsc(String themeCode);
}
