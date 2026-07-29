package com.familya.treeaccess;

import com.familya.treeaccess.application.usecase.CreateTreeUseCase;
import com.familya.treeaccess.application.usecase.GrantMembershipUseCase;
import com.familya.treeaccess.application.usecase.RevokeMembershipUseCase;
import com.familya.treeaccess.application.usecase.TreeLifecycleUseCases;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Instant;

@SpringBootApplication(scanBasePackages = { "com.familya.treeaccess", "com.familya.platform" })
@EnableScheduling
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    @Bean public CreateTreeUseCase.Clock createTreeClock()        { return Instant::now; }
    @Bean public GrantMembershipUseCase.Clock grantMembershipClock() { return Instant::now; }
    @Bean public RevokeMembershipUseCase.Clock revokeMembershipClock() { return Instant::now; }
    @Bean public TreeLifecycleUseCases.Clock lifecycleClock()     { return Instant::now; }
    @Bean public java.time.Clock systemClock() { return java.time.Clock.systemUTC(); }
}