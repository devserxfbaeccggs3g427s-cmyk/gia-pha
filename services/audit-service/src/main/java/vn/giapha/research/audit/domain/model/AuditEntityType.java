package vn.giapha.research.audit.domain.model;

/**
 * Legacy {@code ChangeEntityType}. Albums are audited under {@code MEDIA}
 * with a {@code kind: "ALBUM"} discriminator, exactly like
 * {@code media-service.ts} did.
 */
public enum AuditEntityType {
    MEMBER,
    RELATIONSHIP,
    EVENT,
    MEDIA;

    public static AuditEntityType fromWire(String value) {
        if (value == null) {
            return null;
        }
        return AuditEntityType.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
    }
}
