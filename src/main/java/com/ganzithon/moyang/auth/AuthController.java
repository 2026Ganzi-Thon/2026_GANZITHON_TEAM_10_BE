package com.ganzithon.moyang.auth;

import com.ganzithon.moyang.auth.dto.AuthDtos.LoginRequest;
import com.ganzithon.moyang.auth.dto.AuthDtos.LoginResponse;
import com.ganzithon.moyang.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @Operation(summary = "데모 계정 로그인",
            description = "미리 넣어 둔 데모 계정으로 로그인해 accessToken(JWT, 24시간)을 받아요. 이후 모든 API는 Authorization: Bearer {accessToken} 헤더가 필요해요.")
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(auth.login(request));
    }
}
