package com.example.walletledger.shared.web;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI walletLedgerOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Wallet Ledger API")
                .version("1.0.0")
                .description("Credit, debit, balance, and immutable ledger history for player wallets."));
    }
}
