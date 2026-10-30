package com.mouss.accountservice.config;

import com.mouss.accountservice.domain.Account;
import com.mouss.accountservice.domain.AccountStatus;
import com.mouss.accountservice.repository.AccountRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.UUID;

/**
 * Deterministic demo data so the REST/GraphQL/MCP endpoints and the chatbot have accounts to work with.
 */
@Configuration
public class DataInitializer {

    @Bean
    CommandLineRunner seedAccounts(AccountRepository accountRepository) {
        return args -> {
            if (accountRepository.count() > 0) {
                return;
            }
            List<Account> accounts = List.of(
                    Account.builder()
                            .id(UUID.fromString("11111111-1111-1111-1111-111111111111"))
                            .customerReference("CUST-001")
                            .currency("EUR")
                            .balance(5000.00)
                            .status(AccountStatus.ACTIVE)
                            .build(),
                    Account.builder()
                            .id(UUID.fromString("22222222-2222-2222-2222-222222222222"))
                            .customerReference("CUST-002")
                            .currency("EUR")
                            .balance(2500.00)
                            .status(AccountStatus.ACTIVE)
                            .build(),
                    Account.builder()
                            .id(UUID.fromString("33333333-3333-3333-3333-333333333333"))
                            .customerReference("CUST-003")
                            .currency("EUR")
                            .balance(800.00)
                            .status(AccountStatus.BLOCKED)
                            .build());
            accountRepository.saveAll(accounts);
        };
    }
}
