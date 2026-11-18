package com.ganzithon.moyang.record.adapter;

import com.ganzithon.moyang.common.port.Ports.RecordPort;
import com.ganzithon.moyang.record.service.RecordService;
import org.springframework.stereotype.Component;

@Component
public class RecordPortAdapter implements RecordPort {
    private final RecordService records;
    public RecordPortAdapter(RecordService records){this.records=records;}
    public long create(long userId){return records.create(userId);}
    public void cardReady(long recordId){records.cardReady(recordId);}
}
