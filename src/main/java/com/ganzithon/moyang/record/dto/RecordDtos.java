package com.ganzithon.moyang.record.dto;

import jakarta.validation.constraints.*;
import java.time.*;
import java.util.List;

public final class RecordDtos {
    private RecordDtos() {}
    public record SaveRequest(@NotNull @Positive Long recordId, @NotBlank @Size(max=500) String title) {}
    public record SaveResponse(long recordId, LocalDate recordDate, OffsetDateTime completedAt,
                               int sheepEarned, int sheepBalance) {}
    public record Analysis(String mainElements, String spatialComposition, String lineExpression, String emotionMeaning) {}
    public record Message(int sequenceNo, String role, String content, OffsetDateTime sentAt) {}
    /**
     * Server-only projection of the drawing/AI RecordView. Map drawing.imageUrl/note,
     * analysis, and chronological messages here. The user-provided title comes from SaveRequest.
     * Analysis and its individual fields may be null when the AI has no supported content.
     */
    public record Snapshot(long recordId, String imageUrl,
                           String drawingDescription, Analysis analysis, List<Message> messages) {}
    public record Detail(long recordId, LocalDate recordDate, String imageUrl, String drawingDescription,
                         Analysis analysis, List<Message> messages, OffsetDateTime completedAt, String title) {}
    public record ListItem(long recordId, LocalDate recordDate, String title, String imageUrl, OffsetDateTime completedAt) {}
    public record Page(List<ListItem> items, Long nextCursor, boolean hasNext) {}
    public record Today(boolean completed, Long recordId, LocalDate recordDate) {}
    public record CalendarItem(LocalDate date, long recordId, String imageUrl) {}
    public record Calendar(String month, List<CalendarItem> items) {}
}
