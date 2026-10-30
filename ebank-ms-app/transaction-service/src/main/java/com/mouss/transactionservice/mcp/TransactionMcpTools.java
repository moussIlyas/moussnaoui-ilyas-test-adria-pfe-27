package com.mouss.transactionservice.mcp;

import com.mouss.transactionservice.client.AccountDto;
import com.mouss.transactionservice.domain.Transaction;
import com.mouss.transactionservice.service.CreateTransactionCommand;
import com.mouss.transactionservice.service.TransactionService;
import com.mouss.transactionservice.service.TransferCommand;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * MCP connector for the transaction micro-service.
 * It only delegates to {@link TransactionService}, exactly like the REST and GraphQL controllers.
 */
@Component
public class TransactionMcpTools {

    private final TransactionService transactionService;

    public TransactionMcpTools(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @McpTool(name = "createTransaction",
            description = "Register a payment on an account. The amount must be positive and lower than the configured maximum. "
                    + "The account is read through account-service, its balance is updated, then the transaction is persisted.")
    public Transaction createTransaction(
            @McpToolParam(description = "Account UUID") String accountId,
            @McpToolParam(description = "Amount, strictly positive") double amount,
            @McpToolParam(description = "ISO currency code, e.g. EUR") String currency,
            @McpToolParam(description = "Merchant category, e.g. FOOD, TRAVEL, OTHER", required = false) String merchantCategory,
            @McpToolParam(description = "ISO country code, e.g. FR", required = false) String country) {
        return transactionService.create(new CreateTransactionCommand(
                UUID.fromString(accountId),
                BigDecimal.valueOf(amount),
                currency,
                merchantCategory,
                country));
    }

    @McpTool(name = "transfer",
            description = "Transfer money between two accounts. Both accounts must be active and share the same currency.")
    public List<Transaction> transfer(
            @McpToolParam(description = "Source account UUID") String fromAccountId,
            @McpToolParam(description = "Destination account UUID") String toAccountId,
            @McpToolParam(description = "Amount, strictly positive") double amount,
            @McpToolParam(description = "ISO currency code, e.g. EUR") String currency,
            @McpToolParam(description = "Merchant category", required = false) String merchantCategory,
            @McpToolParam(description = "ISO country code", required = false) String country) {
        return transactionService.transfer(new TransferCommand(
                UUID.fromString(fromAccountId),
                UUID.fromString(toAccountId),
                BigDecimal.valueOf(amount),
                currency,
                merchantCategory,
                country));
    }

    @McpTool(name = "listTransactions",
            description = "List transactions. Pass an account UUID to filter on one account, or leave it empty for all transactions.")
    public List<Transaction> listTransactions(
            @McpToolParam(description = "Optional account UUID", required = false) String accountId) {
        if (StringUtils.hasText(accountId)) {
            return transactionService.findByAccountId(UUID.fromString(accountId));
        }
        return transactionService.findAll();
    }

    @McpTool(name = "getTransaction", description = "Get one transaction by its UUID")
    public Transaction getTransaction(
            @McpToolParam(description = "Transaction UUID") String transactionId) {
        return transactionService.findById(UUID.fromString(transactionId));
    }

    @McpTool(name = "getAccountBalance",
            description = "Read the balance, currency and status of an account through account-service")
    public AccountDto getAccountBalance(
            @McpToolParam(description = "Account UUID") String accountId) {
        return transactionService.getAccountBalance(UUID.fromString(accountId));
    }
}
