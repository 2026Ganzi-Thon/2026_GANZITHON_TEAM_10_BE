package com.ganzithon.moyang.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger 문서 기본 정보. 화면: http://localhost:8080/swagger-ui/index.html
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI moyangOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("모양(MOYANG) API")
                        .version("v1")
                        .description("2026 간지톤 10팀 백엔드 API. 모든 응답은 { success, data, error } 형태입니다. "
                                + "POST /api/auth/login으로 받은 accessToken을 우측 Authorize 버튼에 넣으면 인증이 필요한 API를 호출할 수 있어요."))
                .components(new Components().addSecuritySchemes("bearerAuth",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}
