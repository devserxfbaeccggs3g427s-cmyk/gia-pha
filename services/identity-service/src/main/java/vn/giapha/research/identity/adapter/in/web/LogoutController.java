package vn.giapha.research.identity.adapter.in.web;

import java.time.Instant;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import vn.giapha.research.identity.application.service.AuthSessionService;
import vn.giapha.research.identity.domain.auth.AuthSession;
import vn.giapha.research.identity.infrastructure.kernel.web.ApiSuccess;

/**
 * Logout endpoint (Task 19.5). Revokes the active session (single or all
 * sessions for the user, depending on the {@code ?all=true} flag) and clears
 * the {@code gp-session} cookie.
 */
@RestController
@RequestMapping(path = "/api/auth/logout", produces = "application/json")
public class LogoutController {

    private static final String COOKIE_NAME = "gp-session";

    private final AuthSessionService sessions;

    public LogoutController(AuthSessionService sessions) {
        this.sessions = sessions;
    }

    @PostMapping
    ResponseEntity<ApiSuccess<Void>> logout(HttpServletRequest request,
            HttpServletResponse response,
            @org.springframework.web.bind.annotation.RequestParam(name = "all",
                    defaultValue = "false") boolean all) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof SessionAuthenticationFilter.SessionAuthenticationToken sessionAuth) {
            AuthSession session = sessionAuth.session();
            if (all) {
                sessions.revokeAllForUser(session.userKey(), "USER_LOGOUT_EVERYWHERE",
                        Instant.now());
            } else {
                sessions.revoke(session, "USER_LOGOUT", Instant.now());
            }
        }
        SecurityContextHolder.clearContext();
        response.addHeader(HttpHeaders.SET_COOKIE, clearCookie(request.isSecure()));
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(ApiSuccess.ok(null));
    }

    private static String clearCookie(boolean secure) {
        StringBuilder sb = new StringBuilder();
        sb.append(COOKIE_NAME).append("=; Path=/; HttpOnly; SameSite=Lax; Max-Age=0");
        if (secure) {
            sb.append("; Secure");
        }
        return sb.toString();
    }
}
