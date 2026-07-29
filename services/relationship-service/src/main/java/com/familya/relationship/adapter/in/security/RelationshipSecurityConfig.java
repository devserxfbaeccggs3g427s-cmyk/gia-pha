package com.familya.relationship.adapter.in.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class RelationshipSecurityConfig {

    @Bean
    public SecurityFilterChain relationshipFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/api/v2/trees/**", "/api/v2/internal/**")
            .csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(
                    org.springframework.security.config.http.SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}