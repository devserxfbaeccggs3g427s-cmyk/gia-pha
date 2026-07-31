/**
 * Lớp khởi động (entry point) của dịch vụ Audit & Operations.
 *
 * <p>Audit Ops chỉ làm projection-only theo Task 13.3 / ADR-003 / ADR-007.
 * Nó tiêu thụ {@code operations.events.v1} và {@code saga.replies.v1},
 * cung cấp read-only views và bề mặt operator intent; nó KHÔNG
 * sở hữu Saga state machine, không publish Saga command, không
 * phải business hoặc authorization authority.</p>
 */
package com.familya.auditops;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

@SpringBootApplication(scanBasePackages = {
        "com.familya.auditops",
        "com.familya.platform"
})
@EnableScheduling
public class AuditOpsServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuditOpsServiceApplication.class, args);
    }

    @Bean
    public Clock systemClock() {
        return Clock.systemUTC();
    }
}
