package vn.giapha.research.identity.adapter.in.web;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import vn.giapha.research.identity.application.service.RegistrationService;
import vn.giapha.research.identity.domain.auth.RegistrationInput;
import vn.giapha.research.identity.domain.model.User;
import vn.giapha.research.identity.infrastructure.kernel.web.ApiSuccess;

/**
 * Credential registration endpoint (Task 19.1, Req 2.2). Mirrors the legacy
 * {@code POST /api/auth/register} response contract so the frontend swap is
 * a no-op during cutover.
 */
@RestController
@RequestMapping(path = "/api/auth/register", produces = "application/json")
public class RegistrationController {

    private final RegistrationService registration;

    public RegistrationController(RegistrationService registration) {
        this.registration = registration;
    }

    @PostMapping(consumes = "application/json")
    ApiSuccess<Map<String, Object>> register(@Valid @RequestBody RegistrationRequest body) {
        RegistrationInput input = new RegistrationInput(body.name(), body.email(), body.password());
        RegistrationService.Registered registered =
                registration.register(input, Instant.now());
        User user = registered.user();
        Map<String, Object> data = new LinkedHashMap<>();
        Map<String, Object> userJson = new LinkedHashMap<>();
        userJson.put("id", user.externalId());
        userJson.put("email", user.email());
        userJson.put("name", user.name());
        userJson.put("createdAt", user.createdAt().toString());
        data.put("user", userJson);
        data.put("emailVerificationRequired", registered.emailVerificationRequired());
        data.put("message", registered.emailVerificationRequired()
                ? "Tài khoản đã được tạo. Vui lòng kiểm tra email để xác nhận tài khoản."
                : "Tài khoản đã được tạo. Bạn có thể đăng nhập ngay.");
        return ApiSuccess.ok(data);
    }

    public record RegistrationRequest(
            @NotBlank @Size(min = 2, max = 100) String name,
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(min = 12, max = 72) String password) {}
}
