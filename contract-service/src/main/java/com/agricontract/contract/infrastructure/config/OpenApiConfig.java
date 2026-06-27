package com.agricontract.contract.infrastructure.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI contractServiceOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("AgriContract Contract Service API")
                        .version("v1")
                        .description("Contract lifecycle management. Auth qua header X-User-Id/X-User-Role injected bởi API Gateway, không qua Bearer token trực tiếp."));
    }
}
