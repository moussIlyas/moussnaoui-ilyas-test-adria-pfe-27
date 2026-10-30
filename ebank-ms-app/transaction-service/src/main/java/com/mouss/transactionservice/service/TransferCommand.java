package com.mouss.transactionservice.service;

import java.math.BigDecimal;
import java.util.UUID;

public record TransferCommand(
        UUID fromAccountId,
        UUID toAccountId,
        BigDecimal amount,
        String currency,
        String merchantCategory,
        String country) {
}
