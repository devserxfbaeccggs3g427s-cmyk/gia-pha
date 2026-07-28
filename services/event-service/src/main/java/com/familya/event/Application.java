package com.familya.event;

import com.familya.event.application.usecase.CreateDomainEventUseCase;
import com.familya.event.application.usecase.UpdateDomainEventUseCase;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Instant;

@SpringBootApplication(scanBasePackages = { "com.familya.event", "com.familya.platform" })
@EnableScheduling
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    @Bean public CreateDomainEventUseCase.Clock createClock()      { return Instant::now; }
    @Bean public UpdateDomainEventUseCase.Clock updateClock()      { return Instant::now; }
    @Bean public java.time.Clock systemClock() { return java.time.Clock.systemUTC(); }
}