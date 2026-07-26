package vn.giapha.research.identity.domain.cutover;

/**
 * Which side of the identity cutover is being asked about (Task 20.3).
 * {@code TRANSITION} is the freeze window when neither legacy nor Spring is
 * authoritative and the MySQL-backed compatibility adapter (Task 20.2) is
 * the only legal writer.
 */
public enum IdentityWriter {
    LEGACY,
    SPRING,
    TRANSITION
}
