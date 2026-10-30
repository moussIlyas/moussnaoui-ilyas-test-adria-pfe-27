package com.mouss.transactionservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.UUID;

@FeignClient(name = "account-service", path = "/accounts")
public interface AccountClient {

    @GetMapping("/{id}")
    AccountDto getById(@PathVariable("id") UUID id);

    @PostMapping("/{id}/debit")
    AccountDto debit(@PathVariable("id") UUID id, @RequestBody BalanceOperationRequest request);

    @PostMapping("/{id}/credit")
    AccountDto credit(@PathVariable("id") UUID id, @RequestBody BalanceOperationRequest request);
}
