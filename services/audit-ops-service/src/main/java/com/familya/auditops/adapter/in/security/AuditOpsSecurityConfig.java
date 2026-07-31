package com.familya.auditops.adapter.in.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security configuration for the audit-ops service. The platform
 * {@code SecurityDefaultsConfig} still applies to
 * {@code /actuator/**} and other catch-all routes. This filter chain
 * applies to the audit-ops routes only:
 *
 * <ul>
 *   <li>{@code /api/v2/audit-ops/**}: requires the
 *       {@code AUDIT_OPERATOR} or {@code PLATFORM} role. The role
 *       comes from the platform's bridge token (see
 *       {@code platform-security-starter}) or the platform service
 *       account for backplane calls.</li>
 *   <li>{@code /api/v2/operations/**}: open inside the cluster; the
 *       Gateway validates the caller's identity before forwarding.
 *       Authorization is enforced by the owning service when the
 *       call originates from another service.</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
public class AuditOpsSecurityConfig {

    @Bean
    public SecurityFilterChain auditOpsFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/api/v2/audit-ops/**")
            .csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v2/audit-ops/operations/*/audit").hasAnyRole("AUDIT_OPERATOR", "PLATFORM")
                .requestMatchers("/api/v2/audit-ops/**").hasAnyRole("AUDIT_OPERATOR", "PLATFORM")
                .anyRequest().denyAll());
        return http.build();
    }

    @Bean
    public SecurityFilterChain operationLookupFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/api/v2/operations/**", "/api/v2/audit-ops/operations/**")
            .csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}