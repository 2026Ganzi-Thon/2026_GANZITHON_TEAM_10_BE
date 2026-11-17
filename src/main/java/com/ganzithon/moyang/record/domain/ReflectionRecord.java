package com.ganzithon.moyang.record.domain;

import jakarta.persistence.*;
import java.time.*;
import java.util.*;
import com.ganzithon.moyang.record.dto.RecordDtos.*;

@Entity
@Table(name="records", indexes=@Index(name="idx_records_owner_completion", columnList="userId,status,completedAt,id"))
public class ReflectionRecord {
    public enum Status { IN_PROGRESS, CARD_READY, COMPLETED, DELETED }
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false) private long userId;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private Status status=Status.IN_PROGRESS;
    @Column(length=500) private String title;
    @Column(length=2048) private String imageUrl;
    @Column(columnDefinition="TEXT") private String drawingDescription;
    @Column(columnDefinition="TEXT") private String mainElements;
    @Column(columnDefinition="TEXT") private String spatialComposition;
    @Column(columnDefinition="TEXT") private String lineExpression;
    @Column(columnDefinition="TEXT") private String emotionMeaning;
    @Column(nullable=false) private LocalDateTime createdAt;
    private LocalDate recordDate;
    private LocalDateTime completedAt;
    private LocalDateTime deletedAt;
    @ElementCollection
    @CollectionTable(name="record_messages",joinColumns=@JoinColumn(name="record_id"))
    @OrderColumn(name="message_index")
    private List<RecordMessage> messages=new ArrayList<>();
    protected ReflectionRecord() {}
    public ReflectionRecord(long userId, LocalDateTime now) {this.userId=userId;createdAt=now;}
    public void cardReady() {if(status==Status.IN_PROGRESS)status=Status.CARD_READY;}
    public void complete(Snapshot data, LocalDateTime now) {
        title=data.title(); imageUrl=data.imageUrl(); drawingDescription=data.drawingDescription();
        mainElements=data.analysis().mainElements(); spatialComposition=data.analysis().spatialComposition();
        lineExpression=data.analysis().lineExpression(); emotionMeaning=data.analysis().emotionMeaning();
        messages.clear(); data.messages().forEach(m->messages.add(new RecordMessage(m)));
        completedAt=now;recordDate=now.toLocalDate();status=Status.COMPLETED;
    }
    public void delete(LocalDateTime now) {status=Status.DELETED;deletedAt=now;}
    public Long getId(){return id;}
    public long getUserId(){return userId;}
    public Status getStatus(){return status;}
    public LocalDateTime getCompletedAt(){return completedAt;}
    public LocalDate getRecordDate(){return recordDate;}
    public String getImageUrl(){return imageUrl;}
    public OffsetDateTime completedOffset(){return completedAt.atZone(ZoneId.of("Asia/Seoul")).toOffsetDateTime();}
    public ListItem listItem(){return new ListItem(id,recordDate,title,imageUrl,completedOffset());}
    public Detail detail(){
        List<Message> views=new ArrayList<>();for(int i=0;i<messages.size();i++)views.add(messages.get(i).view(i+1));
        return new Detail(id,recordDate,imageUrl,drawingDescription,new Analysis(mainElements,spatialComposition,lineExpression,emotionMeaning),List.copyOf(views),completedOffset(),title);
    }
}
