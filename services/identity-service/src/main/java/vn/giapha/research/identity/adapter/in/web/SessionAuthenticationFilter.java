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
import jakarta.servlet.http.HttpFilter;

import vn.giapha.research.identity.infrastructure.web.EnvelopeResponseWriter;
import vn.giapha.research.identity.application.service.AuthSessionService;
import vn.giapha.research.identity.domain.auth.AuthSession;

/**
 * Resolves the {@code gp-session} cookie into a Spring authentication and
 * refreshes the idle expiry on every authenticated request (Task 19.5).
 * Sessions past the idle or absolute expiry answer 401, mirroring the legacy
 * NextAuth behavior the frontend already handles.
 */
public class SessionAuthenticationFilter extends HttpFilter {

    private static final long serialVersionUID = 1L;

    public static final String COOKIE_NAME = "gp-session";

    private final AuthSessionService sessions;
    private final EnvelopeResponseWriter responses;

    public SessionAuthenticationFilter(AuthSessionService sessions, EnvelopeResponseWriter responses) {
        this.sessions = sessions;
        this.responses = responses;
    }

    @Override
    protected void doFilter(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws IOException, ServletException {
        String cookie = readCookie(request);
        if (cookie == null) {
            chain.doFilter(request, response);
            return;
        }
        Instant now = Instant.now();
        AuthSession session = sessions.resolve(cookie, now).orElse(null);
        if (session == null) {
            SecurityContextHolder.clearContext();
            responses.write(response, HttpServletResponse.SC_UNAUTHORIZED,
                    "INVALID_SESSION", "Session is invalid or expired");
            return;
        }
        sessions.touch(session, now);
        SessionAuthenticationToken token = new SessionAuthenticationToken(session);
        token.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(token);
        chain.doFilter(request, response);
    }

    private static String readCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (COOKIE_NAME.equals(cookie.getName()) && !cookie.getValue().isBlank()) {
                return cookie.getValue();
            }
        }
        return null;
    }

    public static final class SessionAuthenticationToken extends AbstractAuthenticationToken {

        private static final long serialVersionUID = 1L;

        private final AuthSession session;

        public SessionAuthenticationToken(AuthSession session) {
            super(authoritiesFor(session));
            this.session = session;
        }

        private static Collection<GrantedAuthority> authoritiesFor(AuthSession session) {
            List<GrantedAuthority> authorities = new ArrayList<>();
            authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
            for (String scope : session.scopeList()) {
                authorities.add(new SimpleGrantedAuthority("SCOPE_" + scope));
            }
            return authorities;
        }

        public AuthSession session() {
            return session;
        }

        @Override
        public Object getCredentials() {
            return session.externalId();
        }

        @Override
        public Object getPrincipal() {
            return session.userKey();
        }
    }
}
