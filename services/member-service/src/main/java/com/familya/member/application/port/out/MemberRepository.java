package com.familya.member.application.port.out;

import com.familya.member.domain.model.CanonicalKey;
import com.familya.member.domain.model.Member;
import com.familya.member.domain.model.MemberAuthRow;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MemberRepository {

    void insert(Member member);

    Optional<Member> findById(UUID memberId);

    List<Member> listByTree(UUID treeId, boolean includeTombstoned);

    void update(Member member);

    Optional<UUID> findByCanonicalKey(CanonicalKey key);

    void insertCanonicalKey(CanonicalKey key, UUID memberId);

    void removeCanonicalKey(UUID memberId, CanonicalKey key);

    Optional<MemberAuthRow> findAuth(UUID treeId, UUID userId);

    void upsertAuth(MemberAuthRow row);

    void updateAuthRole(UUID treeId, UUID userId, String role, boolean revoked,
                        long revision, long epoch, java.time.Instant grantedAt,
                        String sourceEventId, java.time.Instant now);

    /**
     * Bulk tombstone every non-tombstoned member in the tree. Default
     * implementation falls back to per-row update so the interface stays
     * binary-compatible; the JdbcMemberRepository override is the
     * optimised path used in production.
     *
     * @return the number of members tombstoned and the highest per-member
     *     version actually reached, so callers can report the version they
     *     truly advanced to rather than an assumed target.
     */
    default BulkTombstoneResult bulkTombstoneByTree(UUID treeId, Instant at) {
        int n = 0;
        long maxVersion = 0L;
        for (Member m : listByTree(treeId, false)) {
            m.tombstone(m.version(), at);
            update(m);
            maxVersion = Math.max(maxVersion, m.version());
            n++;
        }
        return new BulkTombstoneResult(n, maxVersion);
    }

    record BulkTombstoneResult(int affectedCount, long maxAppliedVersion) { }
}