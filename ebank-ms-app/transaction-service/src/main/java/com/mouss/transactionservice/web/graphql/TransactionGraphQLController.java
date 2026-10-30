package com.mouss.transactionservice.web.graphql;

import com.mouss.transactionservice.domain.Transaction;
import com.mouss.transactionservice.service.CreateTransactionCommand;
import com.mouss.transactionservice.service.TransactionService;
import com.mouss.transactionservice.service.TransferCommand;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Controller
public class TransactionGraphQLController {

    private final TransactionService transactionService;

    public TransactionGraphQLController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @QueryMapping
    public List<TransactionView> transactions() {
        return transactionService.findAll().stream().map(TransactionView::from).toList();
    }

    @QueryMapping
    public TransactionView transaction(@Argument String id) {
        return TransactionView.from(transactionService.findById(UUID.fromString(id)));
    }

    @QueryMapping
    public List<TransactionView> transactionsByAccount(@Argument String accountId) {
        return transactionService.findByAccountId(UUID.fromString(accountId))
                .stream().map(TransactionView::from).toList();
    }

    @MutationMapping
    public TransactionView createTransaction(@Argument CreateTransactionInput input) {
        return TransactionView.from(transactionService.create(new CreateTransactionCommand(
                UUID.fromString(input.accountId()),
                BigDecimal.valueOf(input.amount()),
                input.currency(),
                input.merchantCategory(),
                input.country())));
    }

    @MutationMapping
    public List<TransactionView> transfer(@Argument TransferInput input) {
        return transactionService.transfer(new TransferCommand(
                        UUID.fromString(input.fromAccountId()),
                        UUID.fromString(input.toAccountId()),
                        BigDecimal.valueOf(input.amount()),
                        input.currency(),
                        input.merchantCategory(),
                        input.country()))
                .stream().map(TransactionView::from).toList();
    }

    public record CreateTransactionInput(
            String accountId,
            Double amount,
            String currency,
            String merchantCategory,
            String country) {
    }

    public record TransferInput(
            String fromAccountId,
            String toAccountId,
            Double amount,
            String currency,
            String merchantCategory,
            String country) {
    }

    public record TransactionView(
            UUID id,
            UUID accountId,
            BigDecimal amount,
            String currency,
            String merchantCategory,
            String country,
            String createdAt,
            String type) {

        static TransactionView from(Transaction transaction) {
            return new TransactionView(
                    transaction.getId(),
                    transaction.getAccountId(),
                    transaction.getAmount(),
                    transaction.getCurrency(),
                    transaction.getMerchantCategory(),
                    transaction.getCountry(),
                    transaction.getCreatedAt().toString(),
                    transaction.getType().name());
        }
    }
}
