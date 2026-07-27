package com.familya.identity.adapter.in.security;

import com.familya.identity.application.port.out.IdentityRepository;
import com.familya.platform.security.BridgeTokenIssuer;
import com.nimbusds.jwt.SignedJWT;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Configuration
@EnableWebSecurity
public class IdentitySecurityConfig {

    @Bean
    public SecurityFilterChain identityFilterChain(HttpSecurity http,
                                                   SessionAuthenticationFilter filter) throws Exception {
        http
            .securityMatcher("/api/v2/identity/**")
            .csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(org.springframework.security.config.http.SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v2/identity/users", "/api/v2/identity/sessions", "/api/v2/identity/users/*/verify").permitAll()
                .anyRequest().authenticated())
            .addFilterBefore(filter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
class SessionAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger LOG = LoggerFactory.getLogger(SessionAuthenticationFilter.class);

    private final IdentityRepository repo;
    private final BridgeTokenIssuer bridge;
    private final boolean bridgeRequired;

    public SessionAuthenticationFilter(IdentityRepository repo,
                                       @Value("${familya.identity.bridge.required:true}") boolean bridgeRequired) throws Exception {
        this.repo = repo;
        this.bridge = new BridgeTokenIssuer("familya", 300L);
        this.bridgeRequired = bridgeRequired;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!requiresAuth(request)) {
            chain.doFilter(request, response);
            return;
        }
        String token = request.getHeader("X-NextAuth-Bridge-Token");
        if (token == null || token.isBlank()) {
            unauthorized(response, "bridge token missing");
            return;
        }
        try {
            if (!bridge.verify(token)) {
                unauthorized(response, "bridge token invalid");
                return;
            }
            SignedJWT jwt = SignedJWT.parse(token);
            UUID userId = UUID.fromString(jwt.getJWTClaimsSet().getSubject());
            request.setAttribute("familya.principal", userId);
        } catch (Exception e) {
            LOG.debug("Bridge token validation failed", e);
            unauthorized(response, "bridge token malformed");
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean requiresAuth(HttpServletRequest req) {
        String p = req.getRequestURI();
        return !(p.endsWith("/users") || p.endsWith("/sessions") || p.matches(".*/users/[^/]+/verify"));
    }

    private void unauthorized(HttpServletResponse res, String reason) throws IOException {
        res.setStatus(401);
        res.setContentType("application/json");
        res.getWriter().write("{\"code\":\"unauthorized\",\"message\":\"" + reason + "\"}");
    }
}
