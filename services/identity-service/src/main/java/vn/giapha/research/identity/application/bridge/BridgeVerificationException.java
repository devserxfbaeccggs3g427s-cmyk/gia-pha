package vn.giapha.research.identity.application.bridge;

/**
 * Marker for any rejection of a bridge token. The web layer maps this to a
 * 401 with no body detail (so probing attackers cannot distinguish between
 * "bad signature", "expired", "wrong audience" etc.).
 */
public class BridgeVerificationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public BridgeVerificationException(String message) {
        super(message);
    }

    public BridgeVerificationException(String message, Throwable cause) {
        super(message, cause);
    }
}
