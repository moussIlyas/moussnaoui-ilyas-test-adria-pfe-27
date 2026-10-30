package com.mouss.transactionservice.client;

import java.math.BigDecimal;

public record BalanceOperationRequest(BigDecimal amount, String currency) {
}
