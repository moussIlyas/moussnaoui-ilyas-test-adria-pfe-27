package com.mouss.accountservice.web.graphql;

import com.mouss.accountservice.domain.Account;
import com.mouss.accountservice.domain.AccountStatus;
import com.mouss.accountservice.service.AccountService;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Controller
public class AccountGraphQLController {

    private final AccountService accountService;

    public AccountGraphQLController(AccountService accountService) {
        this.accountService = accountService;
    }

    @QueryMapping
    public List<Account> accounts() {
        return accountService.findAll();
    }

    @QueryMapping
    public Account account(@Argument String id) {
        return accountService.findById(UUID.fromString(id));
    }

    @MutationMapping
    public Account createAccount(@Argument CreateAccountInput input) {
        Account account = Account.builder()
                .customerReference(input.customerReference())
                .currency(input.currency())
                .balance(input.balance())
                .status(input.status())
                .build();
        return accountService.create(account);
    }

    @MutationMapping
    public Account debitAccount(@Argument String id, @Argument BalanceOperationInput input) {
        return accountService.debit(UUID.fromString(id), BigDecimal.valueOf(input.amount()), input.currency());
    }

    @MutationMapping
    public Account creditAccount(@Argument String id, @Argument BalanceOperationInput input) {
        return accountService.credit(UUID.fromString(id), BigDecimal.valueOf(input.amount()), input.currency());
    }

    public record CreateAccountInput(String customerReference, String currency, Double balance, AccountStatus status) {
    }

    public record BalanceOperationInput(Double amount, String currency) {
    }
}
