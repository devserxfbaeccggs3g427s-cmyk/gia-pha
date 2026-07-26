package vn.giapha.research.identity.adapter.in.web;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import vn.giapha.research.identity.application.service.AuthSessionService;
import vn.giapha.research.identity.application.service.CredentialLoginService;
import vn.giapha.research.identity.domain.model.User;
import vn.giapha.research.identity.infrastructure.kernel.web.ApiSuccess;

/**
 * Final Spring credential login endpoint (Task 19.3, Req 2.3/2.11).
 *
 * <p>On success the response sets the {@code gp-session} cookie
 * ({@code HttpOnly; SameSite=Lax; Secure} in production; {@code Path=/}) and
 * carries the canonical {@code AuthenticatedUser} payload the legacy
 * {@code NextAuth} client already understands.
 */
@RestController
@RequestMapping(path = "/api/auth/login", produces = "application/json")
public class CredentialLoginController {

    private final CredentialLoginService login;

    public CredentialLoginController(CredentialLoginService login) {
        this.login = login;
    }

    @PostMapping(consumes = "application/json")
    ResponseEntity<ApiSuccess<Map<String, Object>>> authenticate(
            @Valid @RequestBody LoginRequest body) {
        CredentialLoginService.LoginOutcome outcome =
                login.login(body.email(), body.password(), Instant.now());
        return buildSuccessResponse(outcome, true);
    }

    private ResponseEntity<ApiSuccess<Map<String, Object>>> buildSuccessResponse(
            CredentialLoginService.LoginOutcome outcome, boolean secureCookies) {
        AuthSessionService.IssuedSession issued = outcome.session();
        User user = outcome.user();
        Map<String, Object> userJson = new LinkedHashMap<>();
        userJson.put("id", user.externalId());
        userJson.put("email", user.email());
        userJson.put("name", user.name());
        if (user.imageUrl() != null) {
            userJson.put("image", user.imageUrl());
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("user", userJson);
        data.put("expiresAt", issued.absoluteExpiresAt().toString());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, issued.cookieAttributes(secureCookies))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(ApiSuccess.ok(data));
    }

    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank String password) {}
}
