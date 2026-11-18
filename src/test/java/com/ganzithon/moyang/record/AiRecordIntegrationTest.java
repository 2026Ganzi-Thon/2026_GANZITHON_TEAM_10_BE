package com.ganzithon.moyang.record;

import com.ganzithon.moyang.ai.domain.*;
import com.ganzithon.moyang.ai.repository.*;
import com.ganzithon.moyang.drawing.domain.Drawing;
import com.ganzithon.moyang.drawing.repository.DrawingRepository;
import com.ganzithon.moyang.record.port.RecordSnapshotReader;
import com.ganzithon.moyang.record.service.RecordService;
import com.ganzithon.moyang.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:ai-record-integration;MODE=MySQL;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
class AiRecordIntegrationTest {
    @Autowired RecordService records;
    @Autowired RecordSnapshotReader reader;
    @Autowired DrawingRepository drawings;
    @Autowired ConversationRepository conversations;
    @Autowired ConversationMessageRepository messages;
    @Autowired MeetingCardRepository meetingCards;
    @Autowired TransactionTemplate tx;

    @Test void realReaderPersistsAnalysisMessagesAndUserTitleAndPaysOnce() {
        long uid=71001;
        long id=records.create(uid);
        long conversationId=tx.execute(status -> {
            drawings.saveAndFlush(new Drawing(id,uid,null,Drawing.InputType.CANVAS,"나무를 그렸어요",
                    "integration-image.png","image/png",100));
            var c=conversations.saveAndFlush(new Conversation(id,uid));
            messages.saveAndFlush(ConversationMessage.question(c.getId(),"어떤 마음인가요?",1,false));
            messages.saveAndFlush(ConversationMessage.user(c.getId(),ConversationMessage.Type.DONT_KNOW,null,1,false));
            messages.saveAndFlush(ConversationMessage.question(c.getId(),"다른 이야기도 있나요?",2,false));
            messages.saveAndFlush(ConversationMessage.user(c.getId(),ConversationMessage.Type.SKIP,null,2,false));
            c.analyzed("나무",null,"부드러운 선",null);
            c.end("내 말","생각","오늘 이야기",false);
            records.cardReady(id);
            return c.getId();
        });
        assertThatThrownBy(()->records.complete(uid+1,id,"다른 사람 제목")).isInstanceOf(BusinessException.class);
        assertThat(records.complete(uid,id,"  오늘의 나무  ").sheepEarned()).isEqualTo(1);
        var detail=records.detail(uid,id);
        assertThat(detail.title()).isEqualTo("오늘의 나무");
        assertThat(detail.drawingDescription()).isEqualTo("나무를 그렸어요");
        assertThat(detail.analysis().mainElements()).isEqualTo("나무");
        assertThat(detail.analysis().spatialComposition()).isNull();
        assertThat(detail.messages()).extracting(m->m.sequenceNo()).containsExactly(1,2,3,4);
        assertThat(detail.messages()).extracting(m->m.role()).containsExactly("ASSISTANT","USER","ASSISTANT","USER");
        assertThat(detail.messages()).extracting(m->m.content()).containsExactly("어떤 마음인가요?","모르겠어요","다른 이야기도 있나요?","건너뛸게요");
        var source=messages.findByConversationIdOrderByIdAsc(conversationId);
        assertThat(detail.messages().get(0).sentAt().toInstant()).isEqualTo(source.get(0).getCreatedAt().truncatedTo(java.time.temporal.ChronoUnit.SECONDS));
        assertThat(records.complete(uid,id,"새 제목").sheepEarned()).isZero();
        assertThat(records.detail(uid,id).title()).isEqualTo("오늘의 나무");
    }

    @Test void meetingCardWithoutPersonalConversationCanBeSavedAndPaysOnce() {
        long uid=71003,id=records.create(uid);
        tx.executeWithoutResult(status -> {
            var drawing=drawings.saveAndFlush(new Drawing(id,uid,null,Drawing.InputType.CANVAS,
                    "모임에서 그린 그림","meeting-only.png","image/png",100));
            meetingCards.saveAndFlush(new MeetingCard(81003L,uid,id,drawing.getId(),
                    "모임에서 한 말","떠오른 생각","오늘 이야기",false));
            records.cardReady(id);
        });
        assertThat(reader.find(id).orElseThrow().messages()).isEmpty();
        assertThat(records.complete(uid,id,"모임 회고").sheepEarned()).isEqualTo(1);
        var detail=records.detail(uid,id);
        assertThat(detail.title()).isEqualTo("모임 회고");
        assertThat(detail.messages()).isEmpty();
        assertThat(detail.drawingDescription()).isEqualTo("모임에서 그린 그림");
        assertThat(records.complete(uid,id,"다른 제목").sheepEarned()).isZero();
        assertThat(records.detail(uid,id).title()).isEqualTo("모임 회고");
    }

    @Test void drawingWithoutCompletedCardCannotBeSaved() {
        long uid=71002,id=records.create(uid);
        drawings.saveAndFlush(new Drawing(id,uid,null,Drawing.InputType.CANVAS,null,"unready.png","image/png",100));
        assertThat(reader.find(id)).isEmpty();
        records.cardReady(id);
        assertThatThrownBy(()->records.complete(uid,id,"제목")).isInstanceOf(BusinessException.class);
    }
}
