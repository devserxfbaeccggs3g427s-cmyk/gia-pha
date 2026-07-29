package com.familya.platform.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security defaults. Services that need their own authentication
 * filter chain (e.g. identity-service with the NextAuth bridge)
 * override this configuration.
 *
 * <p>Defaults:</p>
 * <ul>
 *   <li>Session creation is STATELESS for cross-service endpoints.</li>
 *   <li>CSRF is disabled for stateless APIs; enabled for any
 *       form-based identity flow.</li>
 *   <li>{@code /actuator/health/**} is publicly readable for liveness
 *       probes; other actuator endpoints require the platform
 *       service-account role.</li>
 *   <li>All other endpoints require authentication.</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
public class SecurityDefaultsConfig {

    @Bean
    public SecurityFilterChain platformFilterChain(HttpSecurity http) throws Exception {
        http
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                .requestMatchers("/actuator/**").hasRole("PLATFORM")
                .anyRequest().authenticated())
            .httpBasic(Customizer.withDefaults());
        return http.build();
    }
}
