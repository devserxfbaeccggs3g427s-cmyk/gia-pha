package com.familya.auditops;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

/**
 * Audit &amp; Operations service entry point. The service owns:
 *
 * <ul>
 *   <li>The public {@code /api/v2/operations} envelope (read &amp; register).</li>
 *   <li>The shared Saga state machine and orchestrator primitives.</li>
 *   <li>The append-only audit log and operator retry surface.</li>
 * </ul>
 *
 * It MUST NOT own business or authorization authority (ADR-003).
 */
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