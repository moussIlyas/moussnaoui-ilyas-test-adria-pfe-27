package com.mouss.transactionservice.web.rest;

import java.math.BigDecimal;
import java.util.UUID;

public record TransferRequest(
        UUID fromAccountId,
        UUID toAccountId,
        BigDecimal amount,
        String currency,
        String merchantCategory,
        String country) {
}
