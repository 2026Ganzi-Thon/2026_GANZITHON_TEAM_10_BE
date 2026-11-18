package com.ganzithon.moyang.record.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import com.ganzithon.moyang.record.dto.RecordDtos.Message;
import java.time.ZoneId;

@Embeddable
public class RecordMessage {
    @Column(nullable=false, length=20) private String role;
    @Column(nullable=false, columnDefinition="TEXT") private String content;
    @Column(nullable=false) private LocalDateTime sentAt;
    protected RecordMessage() {}
    public RecordMessage(Message message) {
        role=message.role(); content=message.content();
        sentAt=message.sentAt().atZoneSameInstant(ZoneId.of("Asia/Seoul")).toLocalDateTime();
    }
    public Message view(int sequence) {
        return new Message(sequence,role,content,sentAt.atZone(ZoneId.of("Asia/Seoul")).toOffsetDateTime());
    }
}
