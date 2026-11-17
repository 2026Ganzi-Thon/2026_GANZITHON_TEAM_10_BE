package com.ganzithon.moyang.topic.config;

import static com.ganzithon.moyang.topic.domain.ThemeCode.DAILY_FUTURE;
import static com.ganzithon.moyang.topic.domain.ThemeCode.LOVE;
import static com.ganzithon.moyang.topic.domain.ThemeCode.MONEY;
import static com.ganzithon.moyang.topic.domain.ThemeCode.RELATION_FAMILY;
import static com.ganzithon.moyang.topic.domain.ThemeCode.SELF_HEALTH;
import static com.ganzithon.moyang.topic.domain.ThemeCode.STUDY_CAREER;
import static com.ganzithon.moyang.topic.domain.ThemeCode.UNSURE;

import com.ganzithon.moyang.topic.domain.ThemeCode;
import com.ganzithon.moyang.topic.domain.Topic;
import com.ganzithon.moyang.topic.repository.TopicRepository;
import java.util.List;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 시작할 때 그림 주제를 넣는다(없으면 만들고, 있으면 문구를 맞춘다). id는 고정값이라 배포마다 같다.
 * 자유 주제(id 1)는 추천 목록에 나오지 않고 그림 업로드의 topicId로만 쓴다.
 * 문구(제목·설명·힌트)는 초안이다. 바꾸고 싶으면 여기만 고치고 다시 실행하면 DB에 반영된다.
 */
@Component
public class TopicSeeder implements ApplicationRunner {

    private final TopicRepository topics;

    public TopicSeeder(TopicRepository topics) {
        this.topics = topics;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        for (Topic seed : seeds()) {
            topics.findById(seed.getId()).ifPresentOrElse(
                    t -> t.change(ThemeCode.of(seed.getThemeCode()).orElse(null),
                            seed.getTitle(), seed.getDescription(), seed.getHints()),
                    () -> topics.save(seed));
        }
    }

    static List<Topic> seeds() {
        return List.of(
                new Topic(Topic.FREE_TOPIC_ID, null, "자유 주제", "떠오르는 대로 자유롭게 그려 보세요", List.of()),

                new Topic(11, STUDY_CAREER, "내가 서 있는 자리", "지금 내가 어디쯤 서 있는지 떠오르는 대로 그려 보세요",
                        List.of("내가 편안함을 느끼는 공간은 어디인가요?", "그 공간에는 어떤 물건을 놓고 싶나요?", "지금 내 마음을 닮은 공간이라면 무엇이 다를까요?")),
                new Topic(12, STUDY_CAREER, "나에게 힘이 되는 것", "작은 것이어도 괜찮아요",
                        List.of("요즘 나를 지탱해 주는 것은 무엇인가요?", "그것은 어떤 모양이나 색일까요?")),
                new Topic(13, STUDY_CAREER, "가고 싶은 길", "앞으로 걷고 싶은 길의 모습을 그려 보세요",
                        List.of("그 길은 곧은가요, 굽어 있나요?", "길 끝에는 무엇이 보이나요?")),

                new Topic(21, LOVE, "마음속 거리", "지금 마음속에 있는 거리감을 그려 보세요",
                        List.of("그 사람과 나 사이에는 무엇이 있나요?", "그 거리는 어떤 색일까요?")),
                new Topic(22, LOVE, "지금 내 마음의 날씨", "맑음, 흐림, 비… 오늘 마음의 날씨는요?",
                        List.of("날씨를 떠올리면 어떤 풍경이 보이나요?", "그 날씨 속에 나는 어디에 있나요?")),
                new Topic(23, LOVE, "나를 안아 주는 장면", "나를 따뜻하게 안아 주는 장면을 그려 보세요",
                        List.of("누가, 혹은 무엇이 나를 안아 주고 있나요?", "그 장면은 어떤 온도일까요?")),

                new Topic(31, MONEY, "내 지갑 속 마음", "돈에 대한 지금의 마음을 모양으로 그려 보세요",
                        List.of("돈을 떠올리면 어떤 모양이 생각나나요?", "그 모양은 무겁나요, 가볍나요?")),
                new Topic(32, MONEY, "여유가 생긴다면", "여유가 생기면 하고 싶은 일을 그려 보세요",
                        List.of("가장 먼저 하고 싶은 일은 무엇인가요?", "그 장면에 누가 함께 있나요?")),
                new Topic(33, MONEY, "내가 지키고 싶은 것", "꼭 지키고 싶은 것을 그려 보세요",
                        List.of("그것을 지키는 나의 모습은 어떤가요?")),

                new Topic(41, DAILY_FUTURE, "오늘의 하루", "오늘 하루를 한 장면으로 그려 보세요",
                        List.of("오늘 가장 기억에 남는 순간은 언제인가요?", "그 순간의 색은 무엇일까요?")),
                new Topic(42, DAILY_FUTURE, "1년 뒤의 나", "1년 뒤 내가 있는 곳을 상상해서 그려 보세요",
                        List.of("그곳은 어떤 풍경인가요?", "그곳에서 나는 무엇을 하고 있나요?")),
                new Topic(43, DAILY_FUTURE, "쉬어 가는 자리", "잠깐 쉬어 갈 수 있는 자리를 그려 보세요",
                        List.of("그 자리에는 무엇이 있으면 좋을까요?")),

                new Topic(51, SELF_HEALTH, "지금의 나", "지금의 나를 모양과 색으로 표현해 보세요",
                        List.of("나를 색으로 말하면 어떤 색인가요?", "그 모양은 둥근가요, 뾰족한가요?")),
                new Topic(52, SELF_HEALTH, "내 몸이 보내는 신호", "몸이 말하고 있는 것을 그려 보세요",
                        List.of("요즘 몸의 어느 부분이 가장 신경 쓰이나요?", "그 느낌을 그림으로 옮기면 어떤 모습일까요?")),
                new Topic(53, SELF_HEALTH, "나를 돌보는 방법", "나를 돌봐 주는 나만의 방법을 그려 보세요",
                        List.of("나를 편안하게 하는 일은 무엇인가요?", "그 일을 할 때의 나는 어떤 표정일까요?")),

                new Topic(61, RELATION_FAMILY, "우리 사이의 거리", "소중한 사람과 나 사이의 거리를 그려 보세요",
                        List.of("그 사람은 어디쯤 서 있나요?", "우리 사이에는 무엇이 놓여 있나요?")),
                new Topic(62, RELATION_FAMILY, "함께 있고 싶은 자리", "함께 있으면 편안한 자리를 그려 보세요",
                        List.of("그 자리에는 누가 있나요?", "그곳은 어떤 분위기인가요?")),
                new Topic(63, RELATION_FAMILY, "하고 싶었던 말", "전하고 싶었던 말을 그림으로 그려 보세요",
                        List.of("그 말을 건네는 순간은 어떤 장면인가요?")),

                new Topic(71, UNSURE, "내가 서 있는 자리", "지금 내가 어디쯤 서 있는지 떠오르는 대로 그려 보세요",
                        List.of("내가 편안함을 느끼는 공간은 어디인가요?", "그 공간에는 어떤 물건을 놓고 싶나요?")),
                new Topic(72, UNSURE, "나에게 힘이 되는 것", "작은 것이어도 괜찮아요",
                        List.of("요즘 나를 지탱해 주는 것은 무엇인가요?", "그것은 어떤 모양이나 색일까요?")),
                new Topic(73, UNSURE, "지금 내 마음의 날씨", "맑음, 흐림, 비… 오늘 마음의 날씨는요?",
                        List.of("날씨를 떠올리면 어떤 풍경이 보이나요?"))
        );
    }
}
