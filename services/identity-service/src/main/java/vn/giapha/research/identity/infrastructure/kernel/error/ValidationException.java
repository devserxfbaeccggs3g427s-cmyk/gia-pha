package vn.giapha.research.identity.infrastructure.kernel.error;

import java.util.Map;

public class ValidationException extends DomainException {
    public ValidationException(String message) {
        super("VALIDATION_ERROR", message);
    }

    public ValidationException(String message, Map<String, Object> details) {
        super("VALIDATION_ERROR", message, details);
    }
}
