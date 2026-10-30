package com.mouss.transactionservice.web.rest;

import com.mouss.transactionservice.domain.Transaction;
import com.mouss.transactionservice.service.CreateTransactionCommand;
import com.mouss.transactionservice.service.TransactionService;
import com.mouss.transactionservice.service.TransferCommand;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class TransactionRestController {

    private final TransactionService transactionService;

    public TransactionRestController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @GetMapping("/transactions")
    public List<Transaction> findAll() {
        return transactionService.findAll();
    }

    @GetMapping("/transactions/{id}")
    public Transaction findById(@PathVariable UUID id) {
        return transactionService.findById(id);
    }

    @GetMapping("/transactions/account/{accountId}")
    public List<Transaction> findByAccount(@PathVariable UUID accountId) {
        return transactionService.findByAccountId(accountId);
    }

    @PostMapping("/transactions")
    @ResponseStatus(HttpStatus.CREATED)
    public Transaction create(@RequestBody CreateTransactionRequest request) {
        return transactionService.create(new CreateTransactionCommand(
                request.accountId(),
                request.amount(),
                request.currency(),
                request.merchantCategory(),
                request.country()));
    }

    @PostMapping("/transfers")
    @ResponseStatus(HttpStatus.CREATED)
    public List<Transaction> transfer(@RequestBody TransferRequest request) {
        return transactionService.transfer(new TransferCommand(
                request.fromAccountId(),
                request.toAccountId(),
                request.amount(),
                request.currency(),
                request.merchantCategory(),
                request.country()));
    }
}
