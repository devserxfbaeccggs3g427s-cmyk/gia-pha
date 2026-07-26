package vn.giapha.research.identity.domain.auth;

import java.util.regex.Pattern;

import vn.giapha.research.identity.domain.model.NewUser;
import vn.giapha.research.identity.infrastructure.kernel.error.ValidationException;

/**
 * Frozen legacy registration validation (Task 19.1, {@code src/data/schemas.ts}).
 * Mirrored here so the Spring API and the legacy Next.js handlers share the
 * exact same business rule:
 *
 * <ul>
 *   <li>name 2–100 characters after trimming;</li>
 *   <li>email normalized (trim + lowercase) and at most 254 characters;</li>
 *   <li>password 12–72 characters with at least one uppercase, one lowercase,
 *       one digit and one special character.</li>
 * </ul>
 */
public final class RegistrationPolicy {

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}$");
    private static final Pattern UPPER = Pattern.compile(".*[A-Z].*");
    private static final Pattern LOWER = Pattern.compile(".*[a-z].*");
    private static final Pattern DIGIT = Pattern.compile(".*\\d.*");
    private static final Pattern SPECIAL = Pattern.compile(".*[^A-Za-z0-9].*");

    private RegistrationPolicy() {
    }

    public static void validate(RegistrationInput input) {
        if (input == null) {
            throw new ValidationException("Registration input is required");
        }
        String name = input.name() == null ? "" : input.name().trim();
        if (name.length() < 2 || name.length() > 100) {
            throw new ValidationException("Name must be 2-100 characters");
        }
        String email = NewUser.normalizeEmail(input.email());
        if (email.length() > 254 || !EMAIL_PATTERN.matcher(email).matches()) {
            throw new ValidationException("Email is invalid");
        }
        String password = input.password();
        if (password == null || password.length() < 12 || password.length() > 72) {
            throw new ValidationException("Password must be 12-72 characters");
        }
        if (!UPPER.matcher(password).matches()
                || !LOWER.matcher(password).matches()
                || !DIGIT.matcher(password).matches()
                || !SPECIAL.matcher(password).matches()) {
            throw new ValidationException(
                    "Password must contain upper, lower, digit and special characters");
        }
    }
}
