package com.ganzithon.moyang.record.controller;

import com.ganzithon.moyang.common.exception.*;
import com.ganzithon.moyang.common.port.Ports.CurrentUserProvider;
import com.ganzithon.moyang.common.response.ApiResponse;
import com.ganzithon.moyang.record.dto.RecordDtos.*;
import com.ganzithon.moyang.record.service.RecordService;
import io.swagger.v3.oas.annotations.Operation;
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
    @Operation(summary="오늘의 활동 마치기 · 회고 저장",description="로그인한 사용자의 준비된 회고를 저장합니다. recordId와 사용자가 입력한 title(필수, 최대 500자)을 전달합니다. "
            + "그림 정보, AI 분석 4개, 대화 내용은 서버에서 조회해 함께 저장합니다. 한국 시간 기준 하루 첫 저장에만 양털 1개를 지급하며 sheepEarned(0 또는 1), sheepBalance를 반환합니다. "
            + "같은 기록의 재요청은 기존 제목과 내용을 유지하고 추가 지급하지 않습니다. 준비되지 않은 기록은 409 RECORD_NOT_READY입니다.")
    @PostMapping public ResponseEntity<ApiResponse<SaveResponse>> complete(@Valid @RequestBody SaveRequest request){return ResponseEntity.status(201).body(ApiResponse.ok(records.complete(userId(),request.recordId(),request.title())));}
    @Operation(summary="회고 기록 목록 조회",description="로그인한 사용자의 저장된 기록을 완료 시각 최신순으로 조회합니다. date(yyyy-MM-dd)를 생략하면 전체 날짜를 조회합니다. "
            + "size는 1~50, 기본 20이며 다음 페이지는 응답의 nextCursor를 cursor로 전달합니다. 삭제되었거나 저장되지 않은 기록은 제외합니다.")
    @GetMapping public ApiResponse<Page> list(@RequestParam(required=false) LocalDate date,@RequestParam(required=false) Long cursor,@RequestParam(defaultValue="20") int size){return ApiResponse.ok(records.list(userId(),date,cursor,size));}
    @Operation(summary="오늘의 회고 완료 여부 조회",description="한국 시간 기준 오늘 저장한 삭제되지 않은 기록의 존재 여부와 최신 recordId를 반환합니다. "
            + "기록이 없으면 completed=false, recordId=null입니다. completed는 추가 저장 가능 여부나 양털 지급 가능 여부를 뜻하지 않습니다.")
    @GetMapping("/today") public ApiResponse<Today> today(){return ApiResponse.ok(records.today(userId()));}
    @Operation(summary="월별 회고 캘린더 조회",description="month를 yyyy-MM 형식으로 전달합니다. 해당 월의 날짜별 최신 저장 기록 이미지와 recordId를 반환합니다. "
            + "같은 날짜의 최신 기록을 삭제하면 이전 기록으로 교체됩니다. month 형식이 올바르지 않으면 400 INVALID_REQUEST입니다.")
    @GetMapping("/calendar") public ApiResponse<Calendar> calendar(@RequestParam String month){long uid=userId();try{return ApiResponse.ok(records.calendar(uid,YearMonth.parse(month)));}catch(java.time.format.DateTimeParseException e){throw new BusinessException(ErrorCode.INVALID_REQUEST);}}
    @Operation(summary="회고 기록 상세 조회",description="본인이 저장한 기록의 제목, 그림 이미지, 그림 설명, AI 분석 4개, 시간순 대화 내용과 완료 시각을 조회합니다. "
            + "분석은 mainElements, spatialComposition, lineExpression, emotionMeaning이며 내용이 없는 항목은 null입니다. "
            + "대화 역할은 USER 또는 ASSISTANT입니다. 다른 사용자의 기록은 403 FORBIDDEN, 없거나 미완료·삭제된 기록은 404 RECORD_NOT_FOUND입니다.")
    @GetMapping("/{recordId}") public ApiResponse<Detail> detail(@PathVariable long recordId){return ApiResponse.ok(records.detail(userId(),recordId));}
    @Operation(summary="회고 기록 삭제",description="본인이 저장한 회고를 논리 삭제합니다. 성공 시 204이며 응답 본문은 없습니다. 지급된 양털은 회수하지 않고 당일 지급 이력도 유지합니다. "
            + "다른 사용자의 기록은 403 FORBIDDEN, 없거나 미완료·이미 삭제된 기록은 404 RECORD_NOT_FOUND입니다.")
    @DeleteMapping("/{recordId}") public ResponseEntity<Void> delete(@PathVariable long recordId){records.delete(userId(),recordId);return ResponseEntity.noContent().build();}
    private long userId(){var user=users.getIfAvailable();if(user==null)throw new BusinessException(ErrorCode.UNAUTHORIZED);long id=user.currentUserId();if(id<1)throw new BusinessException(ErrorCode.UNAUTHORIZED);return id;}
}
