package com.familya.identity.adapter.in.rest;

import com.familya.identity.application.port.in.AuthenticateCommand;
import com.familya.identity.application.port.in.RegisterUserCommand;
import com.familya.identity.application.port.in.RevokeSessionCommand;
import com.familya.identity.application.port.in.VerifyEmailCommand;
import com.familya.identity.application.usecase.AuthenticateUseCase;
import com.familya.identity.application.usecase.RegisterUserUseCase;
import com.familya.identity.application.usecase.RevokeSessionUseCase;
import com.familya.identity.application.usecase.VerifyEmailUseCase;
import com.familya.identity.domain.model.Session;
import com.familya.platform.api.AsyncOperation;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping(path = "/api/v2/identity", produces = MediaType.APPLICATION_JSON_VALUE)
public class IdentityController {

    private final RegisterUserUseCase registerUser;
    private final AuthenticateUseCase authenticate;
    private final VerifyEmailUseCase verifyEmail;
    private final RevokeSessionUseCase revokeSession;
    private final boolean sessionCookieSecure;

    public IdentityController(RegisterUserUseCase registerUser,
                              AuthenticateUseCase authenticate,
                              VerifyEmailUseCase verifyEmail,
                              RevokeSessionUseCase revokeSession,
                              @Value("${familya.identity.session-cookie-secure:true}") boolean sessionCookieSecure) {
        this.registerUser = registerUser;
        this.authenticate = authenticate;
        this.verifyEmail = verifyEmail;
        this.revokeSession = revokeSession;
        this.sessionCookieSecure = sessionCookieSecure;
    }

    @PostMapping("/users")
    public ResponseEntity<AsyncOperation> register(@RequestHeader(name = "Idempotency-Key", required = false) String idem,
                                                  @Valid @RequestBody RegisterUserRequest req,
                                                  HttpServletResponse res) {
        UUID userId = registerUser.execute(new RegisterUserCommand(req.email(), req.password(), req.ipAddress(), req.verificationRequired()));
        res.setHeader("Location", "/api/v2/identity/users/" + userId);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(AsyncOperation.accepted(userId, "/api/v2/operations/" + userId));
    }

    @PostMapping("/sessions")
    public ResponseEntity<SessionResponse> startSession(@RequestHeader(name = "Idempotency-Key", required = false) String idem,
                                                        @Valid @RequestBody AuthenticateRequest req,
                                                        HttpServletResponse res) {
        Session session = authenticate.execute(new AuthenticateCommand(req.email(), req.password(), req.ipAddress(), req.userAgent()));
        String cookie = String.format("identity-session=%s; HttpOnly; %sSameSite=Lax; Path=/; Max-Age=%d",
                session.id(), sessionCookieSecure ? "Secure; " : "", session.absoluteExpiresAt().getEpochSecond() - session.createdAt().getEpochSecond());
        res.setHeader("Set-Cookie", cookie);
        return ResponseEntity.ok(new SessionResponse(session.id().toString(), session.userId().toString(), session.absoluteExpiresAt()));
    }

    @PostMapping("/sessions/current/revoke")
    public ResponseEntity<Void> revokeCurrent(@RequestHeader("X-Acting-User") UUID actingUser,
                                              @RequestHeader("X-Session-Id") UUID sessionId) {
        revokeSession.execute(new RevokeSessionCommand(sessionId, actingUser));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/users/{userId}/verify")
    public ResponseEntity<AsyncOperation> verify(@PathVariable UUID userId, @RequestBody VerifyRequest req) {
        UUID id = verifyEmail.execute(new VerifyEmailCommand(userId, req.token()));
        return ResponseEntity.accepted().body(AsyncOperation.accepted(id, "/api/v2/operations/" + id));
    }

    public record RegisterUserRequest(@Email @NotBlank String email,
                                      @NotBlank @Size(min = 12, max = 256) String password,
                                      String ipAddress,
                                      boolean verificationRequired) { }

    public record AuthenticateRequest(@Email @NotBlank String email,
                                      @NotBlank @Size(min = 12, max = 256) String password,
                                      String ipAddress,
                                      String userAgent) { }

    public record VerifyRequest(@NotBlank String token) { }

    public record SessionResponse(String sessionId, String userId, java.time.Instant absoluteExpiresAt) { }
}
