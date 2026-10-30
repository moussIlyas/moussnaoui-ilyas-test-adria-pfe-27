package com.mouss.transactionservice.client;

import java.util.UUID;

public record AccountDto(
        UUID id,
        String customerReference,
        String status,
        String currency,
        double balance) {
}
