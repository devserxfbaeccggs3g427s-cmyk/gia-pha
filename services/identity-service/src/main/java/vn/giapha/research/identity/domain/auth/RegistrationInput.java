package vn.giapha.research.identity.domain.auth;

/**
 * Credentials provided during registration (Task 19.1, Req 2.2). Validation
 * is enforced by the application service before a {@code users} row is
 * inserted — name 2–100, normalized email ≤ 254, password 12–72 with
 * upper/lower/digit/special requirements. The policy lives in
 * {@link RegistrationPolicy} so it is unit-testable independent of the web
 * layer.
 */
public record RegistrationInput(String name, String email, String password) {}
