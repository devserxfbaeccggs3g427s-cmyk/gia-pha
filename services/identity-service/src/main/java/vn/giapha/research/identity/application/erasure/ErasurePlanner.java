package vn.giapha.research.identity.application.erasure;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Erasure dependency planner (Task 43A, Req 15.15). The preview lists
 * every aggregate affected by erasing a user so a privacy officer can
 * approve the impact before any row is mutated. The dry-run is idempotent
 * and never writes to the database.
 */
public interface ErasurePlanner {

    /**
     * @return plan preview describing every aggregate the erasure would
     *         touch. Field names map to short identifiers the operator UI
     *         displays; counts are exact.
     */
    Preview preview(String externalUserId);

    record Preview(
            String userExternalId,
            List<AffectedRow> affectedRows,
            List<CleanupJob> enqueuedCleanups,
            List<LegalHold> legalHolds,
            List<LegalBlocker> blockers) {

        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("userExternalId", userExternalId);
            map.put("affectedRows", affectedRows.stream().map(AffectedRow::toMap).toList());
            map.put("enqueuedCleanups", enqueuedCleanups.stream().map(CleanupJob::toMap).toList());
            map.put("legalHolds", legalHolds.stream().map(LegalHold::toMap).toList());
            map.put("blockers", blockers.stream().map(LegalBlocker::toMap).toList());
            return map;
        }
    }

    record AffectedRow(String aggregate, String action, long count) {
        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("aggregate", aggregate);
            map.put("action", action);
            map.put("count", count);
            return map;
        }
    }

    record CleanupJob(String reason, long count) {
        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("reason", reason);
            map.put("count", count);
            return map;
        }
    }

    record LegalHold(String aggregate, String reason, Instant until) {
        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("aggregate", aggregate);
            map.put("reason", reason);
            map.put("until", until == null ? null : until.toString());
            return map;
        }
    }

    record LegalBlocker(String aggregate, String reason) {
        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("aggregate", aggregate);
            map.put("reason", reason);
            return map;
        }
    }
}
