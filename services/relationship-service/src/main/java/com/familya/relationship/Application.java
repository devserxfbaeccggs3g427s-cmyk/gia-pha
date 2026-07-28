package com.familya.relationship;

import com.familya.relationship.application.usecase.CreateRelationshipUseCase;
import com.familya.relationship.application.usecase.TombstoneRelationshipUseCase;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Instant;

@SpringBootApplication(scanBasePackages = { "com.familya.relationship", "com.familya.platform" })
@EnableScheduling
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    @Bean public CreateRelationshipUseCase.Clock createClock() { return Instant::now; }
    @Bean public TombstoneRelationshipUseCase.Clock tombstoneClock() { return Instant::now; }
    @Bean public java.time.Clock systemClock() { return java.time.Clock.systemUTC(); }
}