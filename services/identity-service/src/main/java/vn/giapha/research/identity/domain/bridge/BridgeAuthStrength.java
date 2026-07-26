package vn.giapha.research.identity.domain.bridge;

/**
 * Authentication strength carried inside the NextAuth bridge JWT (Req 2.5,
 * Task 18.2). The verifier records what level of trust Next.js placed on the
 * session so backend policy decisions (e.g. step-up for write actions) stay
 * explicit and reviewable.
 */
public enum BridgeAuthStrength {

    /** Default strength: session cookie + standard signing. */
    SESSION(1),

    /** Re-authenticated within the policy window (e.g. password prompt). */
    REAUTHENTICATED(2),

    /** Step-up required: hardware key, MFA, etc. */
    HARDWARE(3);

    private final int level;

    BridgeAuthStrength(int level) {
        this.level = level;
    }

    public int level() {
        return level;
    }

    public boolean atLeast(BridgeAuthStrength other) {
        return this.level >= other.level;
    }
}
