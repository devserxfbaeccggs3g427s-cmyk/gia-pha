package vn.giapha.research.tree.infrastructure.kernel.error;

public class UnauthorizedException extends DomainException {
    public UnauthorizedException(String code, String message) {
        super(code, message);
    }
}
