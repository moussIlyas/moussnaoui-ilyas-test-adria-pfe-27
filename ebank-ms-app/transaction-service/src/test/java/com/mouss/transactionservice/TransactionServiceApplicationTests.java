package com.mouss.transactionservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.kafka.listener.auto-startup=false",
        "spring.kafka.admin.fail-fast=false"
})
class TransactionServiceApplicationTests {

    @Test
    void contextLoads() {
    }
}
