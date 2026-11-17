package com.ganzithon.moyang.sheep.controller;
import com.ganzithon.moyang.common.exception.*;
import com.ganzithon.moyang.common.port.Ports.CurrentUserProvider;
import com.ganzithon.moyang.common.response.ApiResponse;
import com.ganzithon.moyang.sheep.dto.*;
import com.ganzithon.moyang.sheep.service.SheepService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/sheep")
public class SheepController {
    private final SheepService sheep;private final ObjectProvider<CurrentUserProvider> users;
    public SheepController(SheepService sheep,ObjectProvider<CurrentUserProvider> users){this.sheep=sheep;this.users=users;}
    @Operation(summary="내 양털 잔액",description="지갑이 없으면 0. 로그인은 팀 CurrentUserProvider 사용.")
    @GetMapping public ApiResponse<SheepBalanceResponse> balance(){return ApiResponse.ok(sheep.balance(userId()));}
    @Operation(summary="내 양털 획득·사용·환불 내역",description="최신순 커서 조회. size 기본 20, 최대 50. 모임 유형만 meetingId 포함.")
    @GetMapping("/transactions") public ApiResponse<SheepTransactionListResponse> transactions(@RequestParam(required=false) Long cursor,@RequestParam(defaultValue="20") int size){
        return ApiResponse.ok(sheep.transactions(userId(),cursor,size));
    }
    private long userId(){var provider=users.getIfAvailable();if(provider==null)throw new BusinessException(ErrorCode.UNAUTHORIZED);long id=provider.currentUserId();if(id<1)throw new BusinessException(ErrorCode.UNAUTHORIZED);return id;}
}
