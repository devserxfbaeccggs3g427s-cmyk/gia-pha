package com.familya.member;

import com.familya.member.application.usecase.CreateMemberUseCase;
import com.familya.member.application.usecase.MergeMembersUseCase;
import com.familya.member.application.usecase.TombstoneMemberUseCase;
import com.familya.member.application.usecase.UpdateMemberUseCase;
import com.familya.member.application.port.out.MemberRepository;
import com.familya.member.domain.model.MemberAuthRow;
import com.familya.platform.projection.AuthorizationProjection;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@SpringBootApplication(scanBasePackages = { "com.familya.member", "com.familya.platform" })
@EnableScheduling
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    @Bean public CreateMemberUseCase.Clock createClock()      { return Instant::now; }
    @Bean public UpdateMemberUseCase.Clock updateClock()      { return Instant::now; }
    @Bean public TombstoneMemberUseCase.Clock tombstoneClock() { return Instant::now; }
    @Bean public MergeMembersUseCase.Clock mergeClock()       { return Instant::now; }
    @Bean public java.time.Clock systemClock() { return java.time.Clock.systemUTC(); }

    @Bean
    public AuthorizationProjection authorizationProjection(MemberRepository repo,
                                                          @org.springframework.beans.factory.annotation.Value(
                                                                  "${familya.member.authz.freshness-seconds:60}") long freshnessSeconds,
                                                          @org.springframework.beans.factory.annotation.Value(
                                                                  "${familya.member.authz.emergency-budget-ms:250}") long emergencyMs) {
        AuthorizationProjection.ProjectionReader reader = new AuthorizationProjection.ProjectionReader() {
            @Override
            public <T extends AuthorizationProjection.ProjectionRow> Optional<T> find(
                    java.util.UUID aggregateId, java.util.UUID userId, Class<T> rowType) {
                Optional<MemberAuthRow> row = repo.findAuth(aggregateId, userId);
                return row.map(r -> r).flatMap(r -> {
                    if (rowType.isInstance(r)) return Optional.of(rowType.cast(r));
                    return Optional.empty();
                });
            }
        };
        return new AuthorizationProjection(reader,
                Duration.ofSeconds(freshnessSeconds),
                Duration.ofMillis(emergencyMs));
    }
}