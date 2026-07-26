package vn.giapha.research.tree.infrastructure.kernel.error;

public class ForbiddenException extends DomainException {
    public ForbiddenException(String message) {
        super("FORBIDDEN", message);
    }
}
