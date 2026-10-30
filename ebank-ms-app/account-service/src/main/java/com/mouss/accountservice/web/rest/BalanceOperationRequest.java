package com.mouss.accountservice.web.rest;

import java.math.BigDecimal;

public record BalanceOperationRequest(BigDecimal amount, String currency) {
}
