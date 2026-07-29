package com.familya.identity;

import com.familya.identity.application.usecase.RegisterUserUseCase;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;
import java.time.Instant;

/**
 * Identity service entry point. Boots Spring Boot with the platform
 * starters from
 * {@code platform/starters/platform-{spring-boot,outbox,security,observability,resilience,grpc}-starter}.
 */
@SpringBootApplication(scanBasePackages = {
        "com.familya.identity",
        "com.familya.platform"
})
@EnableScheduling
public class IdentityServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(IdentityServiceApplication.class, args);
    }

    @Bean
    public RegisterUserUseCase.Clock clock() {
        return Instant::now;
    }

    @Bean
    public java.time.Clock systemClock() {
        return java.time.Clock.systemUTC();
    }
}
