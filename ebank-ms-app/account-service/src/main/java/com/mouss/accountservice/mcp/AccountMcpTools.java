package com.mouss.accountservice.mcp;

import com.mouss.accountservice.domain.Account;
import com.mouss.accountservice.service.AccountService;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * MCP connector for the account micro-service.
 * It only delegates to {@link AccountService}, exactly like the REST and GraphQL controllers.
 */
@Component
public class AccountMcpTools {

    private final AccountService accountService;

    public AccountMcpTools(AccountService accountService) {
        this.accountService = accountService;
    }

    @McpTool(name = "getAllAccounts", description = "List all bank accounts with their balance, currency and status")
    public List<Account> getAllAccounts() {
        return accountService.findAll();
    }

    @McpTool(name = "getAccountById", description = "Get one bank account by its UUID")
    public Account getAccountById(
            @McpToolParam(description = "The account UUID") String accountId) {
        return accountService.findById(UUID.fromString(accountId));
    }

    @McpTool(name = "createAccount", description = "Create a new bank account")
    public Account createAccount(
            @McpToolParam(description = "Customer reference, e.g. CUST-001") String customerReference,
            @McpToolParam(description = "ISO currency code, e.g. EUR") String currency,
            @McpToolParam(description = "Initial balance") double balance) {
        return accountService.create(Account.builder()
                .customerReference(customerReference)
                .currency(currency)
                .balance(balance)
                .build());
    }

    @McpTool(name = "debitAccount", description = "Debit an amount from an account (fails if the account is blocked, currency mismatches or funds are insufficient)")
    public Account debitAccount(
            @McpToolParam(description = "The account UUID") String accountId,
            @McpToolParam(description = "Amount to debit, strictly positive") double amount,
            @McpToolParam(description = "ISO currency code, e.g. EUR") String currency) {
        return accountService.debit(UUID.fromString(accountId), BigDecimal.valueOf(amount), currency);
    }

    @McpTool(name = "creditAccount", description = "Credit an amount to an account")
    public Account creditAccount(
            @McpToolParam(description = "The account UUID") String accountId,
            @McpToolParam(description = "Amount to credit, strictly positive") double amount,
            @McpToolParam(description = "ISO currency code, e.g. EUR") String currency) {
        return accountService.credit(UUID.fromString(accountId), BigDecimal.valueOf(amount), currency);
    }
}
