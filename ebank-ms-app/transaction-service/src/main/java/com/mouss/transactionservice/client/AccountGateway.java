package com.mouss.transactionservice.client;

import com.mouss.transactionservice.service.AccountServiceUnavailableException;
import com.mouss.transactionservice.service.TransactionException;
import feign.FeignException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Single entry point used by the transaction domain to talk to account-service.
 * Resilience4j wraps every call; a failed/down account-service never blocks the caller with a raw Feign error.
 */
@Service
public class AccountGateway {

    private final AccountClient accountClient;

    public AccountGateway(AccountClient accountClient) {
        this.accountClient = accountClient;
    }

    @CircuitBreaker(name = "accountService", fallbackMethod = "getAccountFallback")
    public AccountDto getAccount(UUID accountId) {
        return accountClient.getById(accountId);
    }

    @CircuitBreaker(name = "accountService", fallbackMethod = "debitFallback")
    public AccountDto debit(UUID accountId, BalanceOperationRequest request) {
        return accountClient.debit(accountId, request);
    }

    @CircuitBreaker(name = "accountService", fallbackMethod = "creditFallback")
    public AccountDto credit(UUID accountId, BalanceOperationRequest request) {
        return accountClient.credit(accountId, request);
    }

    private AccountDto getAccountFallback(UUID accountId, Throwable throwable) {
        throw translate("read account " + accountId, throwable);
    }

    private AccountDto debitFallback(UUID accountId, BalanceOperationRequest request, Throwable throwable) {
        throw translate("debit account " + accountId, throwable);
    }

    private AccountDto creditFallback(UUID accountId, BalanceOperationRequest request, Throwable throwable) {
        throw translate("credit account " + accountId, throwable);
    }

    private RuntimeException translate(String action, Throwable throwable) {
        if (throwable instanceof FeignException feignException) {
            if (feignException.status() == 404) {
                return new TransactionException("Account not found");
            }
            if (feignException.status() == 400) {
                return new TransactionException("Account operation rejected: " + feignException.contentUTF8());
            }
        }
        return new AccountServiceUnavailableException(
                "account-service is unavailable while trying to " + action, throwable);
    }
}
