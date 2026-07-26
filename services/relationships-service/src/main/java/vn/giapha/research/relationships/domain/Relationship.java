package vn.giapha.research.relationships.domain;

import java.time.Instant;
import java.time.LocalDate;
import vn.giapha.research.relationships.support.RelationshipSupport.ValidationException;

/**
 * Canonical relationship edge. For {@code PARENT_CHILD} the source is the
 * parent and the target the child; symmetric types are deduplicated through
 * the database's canonical pair key regardless of direction.
 */
public record Relationship(
        long relationshipKey,
        long treeKey,
        String externalId,
        long sourceMemberKey,
        long targetMemberKey,
        RelationType type,
        String customType,
        LocalDate marriageDate,
        LocalDate divorceDate,
        MarriageStatus marriageStatus,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    public Relationship {
        if (sourceMemberKey == targetMemberKey) {
            throw new ValidationException("sourceMemberId and targetMemberId must be different");
        }
        if (type == RelationType.CUSTOM && (customType == null || customType.isBlank())) {
            throw new ValidationException("customType is required for CUSTOM relationships");
        }
    }
}
