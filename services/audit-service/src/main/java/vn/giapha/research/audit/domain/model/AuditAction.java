package vn.giapha.research.audit.domain.model;

/** Legacy {@code ChangeAction}: the only actions the business audit trail records. */
public enum AuditAction {
    CREATE,
    UPDATE,
    DELETE;

    public static AuditAction fromWire(String value) {
        if (value == null) {
            return null;
        }
        return AuditAction.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
    }
}
