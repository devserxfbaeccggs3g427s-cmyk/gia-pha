package vn.giapha.research.identity.adapter.in.web;

import java.net.URI;
import java.time.Instant;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import vn.giapha.research.identity.application.service.EmailVerificationService;
import vn.giapha.research.identity.domain.model.User;
import vn.giapha.research.identity.infrastructure.kernel.error.ValidationException;

/**
 * Email verification endpoint (Task 19.2, Req 2.3). Mirrors the legacy
 * {@code GET /api/auth/verify-email?token=...} redirect contract: success
 * returns {@code /vi/login?verified=1}, failures carry {@code ?error=...}.
 */
@RestController
public class EmailVerificationController {

    private final EmailVerificationService verification;

    public EmailVerificationController(EmailVerificationService verification) {
        this.verification = verification;
    }

    @GetMapping("/api/auth/verify-email")
    ResponseEntity<Void> verify(@RequestParam(name = "token", required = false) String token) {
        String loginUrl = "/vi/login";
        String errorCode;
        try {
            if (token == null || token.isBlank()) {
                errorCode = "INVALID_TOKEN";
            } else {
                User verified = verification.redeem(token, Instant.now());
                errorCode = verified == null ? "INVALID_TOKEN" : null;
            }
        } catch (ValidationException invalid) {
            errorCode = "INVALID_TOKEN";
        } catch (RuntimeException unexpected) {
            errorCode = "INVALID_TOKEN";
        }
        URI target = errorCode == null
                ? URI.create(loginUrl + "?verified=1")
                : URI.create(loginUrl + "?error=" + errorCode);
        return ResponseEntity.status(HttpStatus.SEE_OTHER)
                .header(HttpHeaders.LOCATION, target.toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .build();
    }
}
