package vn.giapha.research.identity.domain.cutover;

/**
 * Thrown when a writer tries to mutate identity data outside its authority
 * window (Task 20.3, Req 2.8). The web layer maps this to a 503 response so
 * the operator learns about the misrouted write immediately.
 */
public class IdentityWriterForbiddenException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public IdentityWriterForbiddenException(String message) {
        super(message);
    }
}
