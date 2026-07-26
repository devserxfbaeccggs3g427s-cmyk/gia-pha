package vn.giapha.research.identity.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import vn.giapha.research.identity.adapter.in.web.BridgeAuthenticationFilter;
import vn.giapha.research.identity.adapter.in.web.SessionAuthenticationFilter;
import vn.giapha.research.identity.application.bridge.BridgeTokenService;
import vn.giapha.research.identity.application.service.AuthSessionService;
import vn.giapha.research.identity.infrastructure.web.EnvelopeResponseWriter;

@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
public class SecurityConfig {
    @Bean
    SecurityFilterChain identitySecurity(HttpSecurity http, AuthSessionService sessions,
            BridgeTokenService bridge, EnvelopeResponseWriter responses) throws Exception {
        CookieCsrfTokenRepository csrf = CookieCsrfTokenRepository.withHttpOnlyFalse();
        return http
                .csrf(configurer -> configurer.csrfTokenRepository(csrf)
                        .ignoringRequestMatchers("/api/auth/login", "/api/auth/register",
                                "/identity/internal/**"))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers("/actuator/health/**", "/api/auth/login",
                                "/api/auth/register", "/api/auth/verify-email",
                                "/identity/internal/**").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(new BridgeAuthenticationFilter(bridge, responses),
                        UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(new SessionAuthenticationFilter(sessions, responses),
                        BridgeAuthenticationFilter.class)
                .build();
    }
}
