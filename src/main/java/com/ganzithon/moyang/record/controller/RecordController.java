package com.ganzithon.moyang.record.controller;

import com.ganzithon.moyang.common.exception.*;
import com.ganzithon.moyang.common.port.Ports.CurrentUserProvider;
import com.ganzithon.moyang.common.response.ApiResponse;
import com.ganzithon.moyang.record.dto.RecordDtos.*;
import com.ganzithon.moyang.record.service.RecordService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.*;

@RestController @RequestMapping("/api/records")
public class RecordController {
    private final RecordService records;
    private final ObjectProvider<CurrentUserProvider> users;
    public RecordController(RecordService records,ObjectProvider<CurrentUserProvider> users){this.records=records;this.users=users;}
    @PostMapping public ResponseEntity<ApiResponse<SaveResponse>> complete(@Valid @RequestBody SaveRequest request){return ResponseEntity.status(201).body(ApiResponse.ok(records.complete(userId(),request.recordId())));}
    @GetMapping public ApiResponse<Page> list(@RequestParam(required=false) LocalDate date,@RequestParam(required=false) Long cursor,@RequestParam(defaultValue="20") int size){return ApiResponse.ok(records.list(userId(),date,cursor,size));}
    @GetMapping("/today") public ApiResponse<Today> today(){return ApiResponse.ok(records.today(userId()));}
    @GetMapping("/calendar") public ApiResponse<Calendar> calendar(@RequestParam String month){long uid=userId();try{return ApiResponse.ok(records.calendar(uid,YearMonth.parse(month)));}catch(java.time.format.DateTimeParseException e){throw new BusinessException(ErrorCode.INVALID_REQUEST);}}
    @GetMapping("/{recordId}") public ApiResponse<Detail> detail(@PathVariable long recordId){return ApiResponse.ok(records.detail(userId(),recordId));}
    @DeleteMapping("/{recordId}") public ResponseEntity<Void> delete(@PathVariable long recordId){records.delete(userId(),recordId);return ResponseEntity.noContent().build();}
    private long userId(){var user=users.getIfAvailable();if(user==null)throw new BusinessException(ErrorCode.UNAUTHORIZED);long id=user.currentUserId();if(id<1)throw new BusinessException(ErrorCode.UNAUTHORIZED);return id;}
}
