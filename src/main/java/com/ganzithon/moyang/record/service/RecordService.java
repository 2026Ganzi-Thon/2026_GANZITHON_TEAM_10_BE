package com.ganzithon.moyang.record.service;

import com.ganzithon.moyang.common.exception.*;
import com.ganzithon.moyang.record.domain.ReflectionRecord;
import com.ganzithon.moyang.record.domain.ReflectionRecord.Status;
import com.ganzithon.moyang.record.dto.RecordDtos.*;
import com.ganzithon.moyang.record.port.RecordSnapshotReader;
import com.ganzithon.moyang.record.repository.RecordRepository;
import com.ganzithon.moyang.sheep.service.SheepService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.Optional;
import java.util.Set;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

@Service @Transactional(readOnly=true)
public class RecordService {
    private static final ZoneId SEOUL=ZoneId.of("Asia/Seoul");
    private final RecordRepository repository;
    private final SheepService sheep;
    private final ObjectProvider<RecordSnapshotReader> readers;
    private final Clock clock;
    private final EntityManager entityManager;
    public RecordService(RecordRepository repository,SheepService sheep,ObjectProvider<RecordSnapshotReader> readers,Clock clock,EntityManager entityManager){
        this.repository=repository;this.sheep=sheep;this.readers=readers;this.clock=clock;this.entityManager=entityManager;
    }
    @Transactional public long create(long userId){positive(userId);return repository.saveAndFlush(new ReflectionRecord(userId,now())).getId();}
    @Transactional public void cardReady(long recordId){positive(recordId);var r=lock(recordId);r.cardReady();}
    @Transactional public SaveResponse complete(long userId,long recordId){
        positive(userId);positive(recordId);
        var r=owned(userId,Optional.of(lock(recordId)));
        if(r.getStatus()==Status.COMPLETED)return new SaveResponse(r.getId(),r.getRecordDate(),r.completedOffset(),0,sheep.balance(userId).balance());
        if(r.getStatus()!=Status.CARD_READY)throw error(ErrorCode.RECORD_NOT_READY);
        var reader=readers.getIfAvailable();if(reader==null)throw error(ErrorCode.RECORD_SOURCE_UNAVAILABLE);
        var data=reader.find(recordId).orElseThrow(()->error(ErrorCode.RECORD_NOT_READY));
        validate(data,userId,recordId);
        var completedAt=now();
        r.complete(data,completedAt);repository.flush();
        var award=sheep.awardRecordAt(userId,recordId,completedAt);
        return new SaveResponse(recordId,r.getRecordDate(),r.completedOffset(),award.sheepEarned(),award.sheepBalance());
    }
    public Detail detail(long userId,long id){var r=owned(userId,repository.findById(id));if(r.getStatus()!=Status.COMPLETED)throw error(ErrorCode.RECORD_NOT_FOUND);return r.detail();}
    @Transactional public void delete(long userId,long id){positive(userId);positive(id);var r=owned(userId,Optional.of(lock(id)));if(r.getStatus()!=Status.COMPLETED)throw error(ErrorCode.RECORD_NOT_FOUND);r.delete(now());}
    public Today today(long userId){positive(userId);var date=now().toLocalDate();var rows=repository.page(userId,Status.COMPLETED,date,null,null,PageRequest.of(0,1));return new Today(!rows.isEmpty(),rows.isEmpty()?null:rows.get(0).getId(),date);}
    public Page list(long userId,LocalDate date,Long cursor,int size){
        positive(userId);if(size<1||size>50||(cursor!=null&&cursor<1))throw error(ErrorCode.INVALID_REQUEST);
        ReflectionRecord anchor=null;
        if(cursor!=null){anchor=repository.findById(cursor).orElseThrow(()->error(ErrorCode.INVALID_REQUEST));
            // Deleted anchors retain completion time so deleting a page boundary does not break pagination.
            if(anchor.getUserId()!=userId||anchor.getCompletedAt()==null)throw error(ErrorCode.INVALID_REQUEST);}
        var rows=repository.page(userId,Status.COMPLETED,date,anchor==null?null:anchor.getCompletedAt(),cursor,PageRequest.of(0,size+1));
        boolean more=rows.size()>size;var selected=rows.subList(0,Math.min(size,rows.size()));
        return new Page(selected.stream().map(ReflectionRecord::listItem).toList(),more?selected.get(selected.size()-1).getId():null,more);
    }
    public Calendar calendar(long userId,YearMonth month){positive(userId);if(month==null||month.getYear()<1||month.getYear()>9998)throw error(ErrorCode.INVALID_REQUEST);
        return new Calendar(month.toString(),repository.calendar(userId,Status.COMPLETED,month.atDay(1),month.plusMonths(1).atDay(1)).stream().map(r->new CalendarItem(r.getRecordDate(),r.getId(),r.getImageUrl())).toList());}
    private ReflectionRecord owned(long userId,Optional<ReflectionRecord> value){positive(userId);var r=value.orElseThrow(()->error(ErrorCode.RECORD_NOT_FOUND));if(r.getUserId()!=userId)throw error(ErrorCode.FORBIDDEN);if(r.getStatus()==Status.DELETED)throw error(ErrorCode.RECORD_NOT_FOUND);return r;}
    private ReflectionRecord lock(long id){
        var record=repository.lock(id).orElseThrow(()->error(ErrorCode.RECORD_NOT_FOUND));
        entityManager.refresh(record,LockModeType.PESSIMISTIC_WRITE);
        return record;
    }
    private void validate(Snapshot s,long uid,long id){
        if(s.userId()!=uid||s.recordId()!=id)throw error(ErrorCode.FORBIDDEN);
        if(!s.ready()||blank(s.title())||s.title().length()>500||blank(s.imageUrl())||s.imageUrl().length()>2048||s.analysis()==null||s.messages()==null||s.messages().isEmpty())throw error(ErrorCode.RECORD_NOT_READY);
        var a=s.analysis();if(blank(a.mainElements())||blank(a.spatialComposition())||blank(a.lineExpression())||blank(a.emotionMeaning()))throw error(ErrorCode.RECORD_NOT_READY);
        int seq=1;for(var m:s.messages()){if(m==null||m.sequenceNo()!=seq++||!Set.of("USER","ASSISTANT").contains(m.role()==null?"":m.role())||blank(m.content())||m.sentAt()==null)throw error(ErrorCode.RECORD_NOT_READY);}
    }
    private static boolean blank(String s){return s==null||s.isBlank();}
    private static void positive(long n){if(n<1)throw error(ErrorCode.INVALID_REQUEST);}
    private static BusinessException error(ErrorCode c){return new BusinessException(c);}
    private LocalDateTime now(){return LocalDateTime.ofInstant(clock.instant(),SEOUL);}
}
