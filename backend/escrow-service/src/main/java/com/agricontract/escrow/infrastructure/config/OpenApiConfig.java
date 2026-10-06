package com.agricontract.escrow.infrastructure.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI escrowServiceOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("AgriContract Escrow Service API")
                        .version("v1")
                        .description("EscrowAccount + EscrowTransaction ledger. Auth qua header X-User-Id/X-User-Role injected bởi API Gateway, không qua Bearer token trực tiếp."));
    }
}
