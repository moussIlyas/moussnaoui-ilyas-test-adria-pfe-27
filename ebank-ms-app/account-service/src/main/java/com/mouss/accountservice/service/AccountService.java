package com.mouss.accountservice.service;

import com.mouss.accountservice.domain.Account;
import com.mouss.accountservice.domain.AccountStatus;
import com.mouss.accountservice.repository.AccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@Transactional
public class AccountService {

    private final AccountRepository accountRepository;

    public AccountService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Transactional(readOnly = true)
    public List<Account> findAll() {
        return accountRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Account findById(UUID id) {
        return accountRepository.findById(id)
                .orElseThrow(() -> new AccountNotFoundException(id));
    }

    public Account create(Account account) {
        if (!StringUtils.hasText(account.getCustomerReference())) {
            throw new AccountOperationException("customerReference is required");
        }
        if (!StringUtils.hasText(account.getCurrency())) {
            throw new AccountOperationException("currency is required");
        }
        if (account.getBalance() < 0) {
            throw new AccountOperationException("balance must be positive or zero");
        }
        account.setId(account.getId() == null ? UUID.randomUUID() : account.getId());
        account.setCurrency(normalizeCurrency(account.getCurrency()));
        account.setStatus(account.getStatus() == null ? AccountStatus.ACTIVE : account.getStatus());
        return accountRepository.save(account);
    }

    public Account debit(UUID id, BigDecimal amount, String currency) {
        Account account = requireOperableAccount(id, amount, currency);
        BigDecimal balance = BigDecimal.valueOf(account.getBalance());
        if (balance.compareTo(amount) < 0) {
            throw new AccountOperationException(
                    "Insufficient funds on account " + id + ": balance=" + balance + ", requested=" + amount);
        }
        account.setBalance(balance.subtract(amount).doubleValue());
        return accountRepository.save(account);
    }

    public Account credit(UUID id, BigDecimal amount, String currency) {
        Account account = requireOperableAccount(id, amount, currency);
        BigDecimal balance = BigDecimal.valueOf(account.getBalance());
        account.setBalance(balance.add(amount).doubleValue());
        return accountRepository.save(account);
    }

    private Account requireOperableAccount(UUID id, BigDecimal amount, String currency) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new AccountOperationException("amount must be strictly positive");
        }
        Account account = findById(id);
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new AccountOperationException("Account " + id + " is " + account.getStatus());
        }
        if (!account.getCurrency().equalsIgnoreCase(normalizeCurrency(currency))) {
            throw new AccountOperationException(
                    "Currency mismatch on account " + id + ": account=" + account.getCurrency() + ", operation=" + currency);
        }
        return account;
    }

    private String normalizeCurrency(String currency) {
        if (!StringUtils.hasText(currency)) {
            throw new AccountOperationException("currency is required");
        }
        return currency.toUpperCase(Locale.ROOT);
    }
}
