package vn.giapha.research.identity.application.bridge;

/**
 * Operator-facing exception thrown when a bridge issuance or verification is
 * rejected because the runtime kill switch is active (Task 18.4). Distinct
 * from {@link BridgeVerificationException} so the web layer can return 503
 * vs 401 without ambiguity.
 */
public class BridgeKilledException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public BridgeKilledException(String message) {
        super(message);
    }
}
