package com.refund.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Metadata for the auto-generated OpenAPI spec / Swagger UI. */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI refundOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Smart Refund Orchestration Service")
                .version("1.0.0")
                .description("Orchestrates refunds across Stripe, Adyen, and LegacyPay with "
                        + "per-provider rules, risk scoring, and an approval gate for large/high-risk refunds."));
    }
}
