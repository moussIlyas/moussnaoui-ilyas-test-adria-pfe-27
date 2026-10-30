package com.mouss.transactionservice.service;

import com.mouss.transactionservice.client.AccountDto;
import com.mouss.transactionservice.client.AccountGateway;
import com.mouss.transactionservice.client.BalanceOperationRequest;
import com.mouss.transactionservice.domain.Transaction;
import com.mouss.transactionservice.domain.TransactionType;
import com.mouss.transactionservice.messaging.TransactionEventPublisher;
import com.mouss.transactionservice.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class TransactionService {

    private static final Logger log = LoggerFactory.getLogger(TransactionService.class);

    private final TransactionRepository transactionRepository;
    private final AccountGateway accountGateway;
    private final TransactionEventPublisher eventPublisher;
    private final BigDecimal maxAmount;

    public TransactionService(TransactionRepository transactionRepository,
                              AccountGateway accountGateway,
                              TransactionEventPublisher eventPublisher,
                              @Value("${bank.transaction.max-amount:1000}") BigDecimal maxAmount) {
        this.transactionRepository = transactionRepository;
        this.accountGateway = accountGateway;
        this.eventPublisher = eventPublisher;
        this.maxAmount = maxAmount;
    }

    @Transactional
    public Transaction create(CreateTransactionCommand command) {
        validateAmount(command.amount());
        String currency = normalizeCurrency(command.currency());
        AccountDto account = accountGateway.getAccount(command.accountId());
        ensureOperable(account, currency);
        ensureSufficientFunds(account, command.amount());

        // Business rule: consult the balance, then update it through account-service.
        accountGateway.debit(command.accountId(), new BalanceOperationRequest(command.amount(), currency));

        Transaction transaction = newTransaction(
                command.accountId(),
                command.amount(),
                currency,
                command.merchantCategory(),
                command.country(),
                TransactionType.PAYMENT);

        try {
            Transaction saved = transactionRepository.saveAndFlush(transaction);
            eventPublisher.publish(saved);
            return saved;
        } catch (RuntimeException exception) {
            compensateCredit(command.accountId(), command.amount(), currency);
            throw exception;
        }
    }

    @Transactional
    public List<Transaction> transfer(TransferCommand command) {
        validateAmount(command.amount());
        if (command.fromAccountId().equals(command.toAccountId())) {
            throw new TransactionException("fromAccountId and toAccountId must be different");
        }
        String currency = normalizeCurrency(command.currency());

        AccountDto from = accountGateway.getAccount(command.fromAccountId());
        AccountDto to = accountGateway.getAccount(command.toAccountId());
        ensureOperable(from, currency);
        ensureOperable(to, currency);
        ensureSufficientFunds(from, command.amount());

        BalanceOperationRequest operation = new BalanceOperationRequest(command.amount(), currency);
        accountGateway.debit(command.fromAccountId(), operation);
        try {
            accountGateway.credit(command.toAccountId(), operation);
        } catch (RuntimeException exception) {
            compensateCredit(command.fromAccountId(), command.amount(), currency);
            throw exception;
        }

        Transaction debit = newTransaction(
                command.fromAccountId(), command.amount(), currency,
                command.merchantCategory(), command.country(), TransactionType.TRANSFER_DEBIT);
        Transaction credit = newTransaction(
                command.toAccountId(), command.amount(), currency,
                command.merchantCategory(), command.country(), TransactionType.TRANSFER_CREDIT);

        try {
            Transaction savedDebit = transactionRepository.saveAndFlush(debit);
            Transaction savedCredit = transactionRepository.saveAndFlush(credit);
            eventPublisher.publish(savedDebit);
            eventPublisher.publish(savedCredit);
            return List.of(savedDebit, savedCredit);
        } catch (RuntimeException exception) {
            compensateTransfer(command, currency);
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public List<Transaction> findAll() {
        return transactionRepository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public Transaction findById(UUID id) {
        return transactionRepository.findById(id)
                .orElseThrow(() -> new TransactionException("Transaction not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<Transaction> findByAccountId(UUID accountId) {
        return transactionRepository.findByAccountIdOrderByCreatedAtDesc(accountId);
    }

    /**
     * Read-only balance lookup exposed to the chatbot through MCP.
     */
    @Transactional(readOnly = true)
    public AccountDto getAccountBalance(UUID accountId) {
        return accountGateway.getAccount(accountId);
    }

    private void validateAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new TransactionException("amount must be strictly positive");
        }
        if (amount.compareTo(maxAmount) > 0) {
            throw new TransactionException(
                    "amount " + amount + " exceeds the configured maximum " + maxAmount);
        }
    }

    private void ensureOperable(AccountDto account, String currency) {
        if (!"ACTIVE".equalsIgnoreCase(account.status())) {
            throw new TransactionException("Account " + account.id() + " is " + account.status());
        }
        if (!account.currency().equalsIgnoreCase(currency)) {
            throw new TransactionException(
                    "Currency mismatch on account " + account.id() + ": account=" + account.currency() + ", transaction=" + currency);
        }
    }

    private void ensureSufficientFunds(AccountDto account, BigDecimal amount) {
        if (BigDecimal.valueOf(account.balance()).compareTo(amount) < 0) {
            throw new TransactionException(
                    "Insufficient funds on account " + account.id() + ": balance=" + account.balance() + ", requested=" + amount);
        }
    }

    private Transaction newTransaction(UUID accountId, BigDecimal amount, String currency,
                                       String merchantCategory, String country, TransactionType type) {
        return Transaction.builder()
                .id(UUID.randomUUID())
                .accountId(accountId)
                .amount(amount)
                .currency(currency)
                .merchantCategory(StringUtils.hasText(merchantCategory) ? merchantCategory : "OTHER")
                .country(StringUtils.hasText(country) ? country : "FR")
                .createdAt(Instant.now())
                .type(type)
                .build();
    }

    private String normalizeCurrency(String currency) {
        if (!StringUtils.hasText(currency)) {
            throw new TransactionException("currency is required");
        }
        return currency.toUpperCase(Locale.ROOT);
    }

    private void compensateCredit(UUID accountId, BigDecimal amount, String currency) {
        try {
            accountGateway.credit(accountId, new BalanceOperationRequest(amount, currency));
        } catch (RuntimeException compensationFailure) {
            log.error("Could not compensate credit on account {} for amount {} {}", accountId, amount, currency, compensationFailure);
        }
    }

    private void compensateTransfer(TransferCommand command, String currency) {
        BalanceOperationRequest operation = new BalanceOperationRequest(command.amount(), currency);
        try {
            accountGateway.debit(command.toAccountId(), operation);
        } catch (RuntimeException exception) {
            log.error("Could not reverse the credit on account {}", command.toAccountId(), exception);
        }
        try {
            accountGateway.credit(command.fromAccountId(), operation);
        } catch (RuntimeException exception) {
            log.error("Could not reverse the debit on account {}", command.fromAccountId(), exception);
        }
    }
}
