package com.ganzithon.moyang.record.adapter;

import com.ganzithon.moyang.ai.dto.ConversationDtos.MessageView;
import com.ganzithon.moyang.ai.service.ConversationService;
import com.ganzithon.moyang.record.dto.RecordDtos.*;
import com.ganzithon.moyang.record.port.RecordSnapshotReader;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Reads persisted AI results; never generates a new analysis during completion. */
@Component
@Transactional(readOnly=true)
public class AiRecordSnapshotReader implements RecordSnapshotReader {
    private final ConversationService conversations;

    public AiRecordSnapshotReader(ConversationService conversations) {
        this.conversations = conversations;
    }

    @Override
    public Optional<Snapshot> find(long recordId) {
        return conversations.recordView(recordId)
                .filter(view -> view.card() != null && view.drawing() != null)
                .map(view -> {
                    var analysis = view.analysis();
                    Analysis mapped = analysis == null ? null : new Analysis(analysis.mainElements(),
                            analysis.spatialComposition(), analysis.lineExpression(), analysis.emotionMeaning());
                    List<Message> messages = new ArrayList<>();
                    for (MessageView message : view.messages()) {
                        String content = switch (message.type()) {
                            case "SKIP" -> "건너뛸게요";
                            case "DONT_KNOW" -> "모르겠어요";
                            default -> message.content();
                        };
                        messages.add(new Message(messages.size()+1,
                                "AI".equals(message.role()) ? "ASSISTANT" : message.role(),
                                content, message.createdAt()));
                    }
                    return new Snapshot(view.recordId(), view.drawing().imageUrl(), view.drawing().note(),
                            mapped, List.copyOf(messages));
                });
    }
}
