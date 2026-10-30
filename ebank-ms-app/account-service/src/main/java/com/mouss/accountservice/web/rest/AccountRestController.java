package com.mouss.accountservice.web.rest;

import com.mouss.accountservice.domain.Account;
import com.mouss.accountservice.service.AccountService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/accounts")
public class AccountRestController {

    private final AccountService accountService;

    public AccountRestController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping
    public List<Account> findAll() {
        return accountService.findAll();
    }

    @GetMapping("/{id}")
    public Account findById(@PathVariable UUID id) {
        return accountService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Account create(@RequestBody Account account) {
        return accountService.create(account);
    }

    @PostMapping("/{id}/debit")
    public Account debit(@PathVariable UUID id, @RequestBody BalanceOperationRequest request) {
        return accountService.debit(id, request.amount(), request.currency());
    }

    @PostMapping("/{id}/credit")
    public Account credit(@PathVariable UUID id, @RequestBody BalanceOperationRequest request) {
        return accountService.credit(id, request.amount(), request.currency());
    }
}
