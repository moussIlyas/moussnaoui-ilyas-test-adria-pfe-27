package com.mouss.transactionservice.web.rest;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateTransactionRequest(
        UUID accountId,
        BigDecimal amount,
        String currency,
        String merchantCategory,
        String country) {
}
