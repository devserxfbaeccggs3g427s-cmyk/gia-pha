package vn.giapha.research.identity.adapter.in.web;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import vn.giapha.research.identity.infrastructure.web.EnvelopeResponseWriter;
import vn.giapha.research.identity.application.bridge.BridgeKilledException;
import vn.giapha.research.identity.domain.bridge.BridgeToken;
import vn.giapha.research.identity.application.bridge.BridgeTokenService;
import vn.giapha.research.identity.application.bridge.BridgeVerificationException;

/**
 * Servlet filter that resolves the NextAuth bridge JWT into a Spring
 * authentication (Task 18, Req 2.5-2.7). The token is read from either the
 * {@code Authorization: Bearer} header or the {@code gp-bridge} HttpOnly
 * cookie issued by the Next.js exchange endpoint. Verifications are pinned
 * to the configured issuer/audience, algorithm, key, expiry and replay
 * behavior; any failure short-circuits to a uniform 401 response so probing
 * attackers cannot distinguish the failure mode.
 */
public class BridgeAuthenticationFilter extends jakarta.servlet.http.HttpFilter {

    private static final long serialVersionUID = 1L;

    public static final String BEARER_PREFIX = "Bearer ";
    public static final String BRIDGE_COOKIE = "gp-bridge";
    public static final String PRINCIPAL_ATTRIBUTE = "giapha.bridge.principal";

    private final BridgeTokenService bridge;
    private final EnvelopeResponseWriter responses;

    public BridgeAuthenticationFilter(BridgeTokenService bridge, EnvelopeResponseWriter responses) {
        this.bridge = bridge;
        this.responses = responses;
    }

    @Override
    protected void doFilter(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws IOException, ServletException {
        String token = extractToken(request);
        if (token == null) {
            chain.doFilter(request, response);
            return;
        }
        try {
            BridgeToken verified = bridge.verify(token);
            BridgeAuthenticationToken auth = new BridgeAuthenticationToken(verified);
            auth.setDetails(new BridgeDetails(verified.issuedAt(), verified.expiresAt()));
            SecurityContextHolder.getContext().setAuthentication(auth);
            request.setAttribute(PRINCIPAL_ATTRIBUTE, verified);
            chain.doFilter(request, response);
        } catch (BridgeKilledException killed) {
            SecurityContextHolder.clearContext();
            responses.write(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "BRIDGE_DISABLED", "Bridge authentication is disabled");
        } catch (BridgeVerificationException invalid) {
            SecurityContextHolder.clearContext();
            responses.write(response, HttpServletResponse.SC_UNAUTHORIZED,
                    "INVALID_TOKEN", "Invalid bridge token");
        }
    }

    private static String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length()).trim();
        }
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (BRIDGE_COOKIE.equals(cookie.getName()) && !cookie.getValue().isBlank()) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    /**
     * Authentication populated from a verified bridge token. Granted authorities
     * are limited to the {@code ROLE_USER} baseline plus any scope-derived
     * authorities; the step-up {@code strength} is carried in the principal so
     * downstream policy can require higher levels for sensitive operations.
     */
    public static final class BridgeAuthenticationToken extends AbstractAuthenticationToken {

        private static final long serialVersionUID = 1L;

        private final BridgeToken token;

        public BridgeAuthenticationToken(BridgeToken token) {
            super(authoritiesFor(token));
            this.token = token;
            setAuthenticated(true);
        }

        private static Collection<GrantedAuthority> authoritiesFor(BridgeToken token) {
            List<GrantedAuthority> authorities = new ArrayList<>();
            authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
            for (String scope : token.scopes()) {
                authorities.add(new SimpleGrantedAuthority("SCOPE_" + scope));
            }
            return authorities;
        }

        @Override
        public Object getCredentials() {
            return token.tokenId();
        }

        @Override
        public Object getPrincipal() {
            return token.userExternalId();
        }

        public BridgeToken bridgeToken() {
            return token;
        }
    }

    public record BridgeDetails(Instant issuedAt, Instant expiresAt) {}
}
