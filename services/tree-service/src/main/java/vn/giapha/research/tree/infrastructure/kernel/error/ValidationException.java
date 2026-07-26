package vn.giapha.research.tree.infrastructure.kernel.error;

public class ValidationException extends DomainException {
    public ValidationException(String message) {
        super("VALIDATION_ERROR", message);
    }
}
