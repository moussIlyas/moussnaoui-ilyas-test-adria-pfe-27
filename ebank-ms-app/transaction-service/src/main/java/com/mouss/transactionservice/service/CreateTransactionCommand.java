package com.mouss.transactionservice.service;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateTransactionCommand(
        UUID accountId,
        BigDecimal amount,
        String currency,
        String merchantCategory,
        String country) {
}
