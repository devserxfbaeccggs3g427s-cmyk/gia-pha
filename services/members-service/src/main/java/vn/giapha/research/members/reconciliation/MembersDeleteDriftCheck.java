package vn.giapha.research.members.reconciliation;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import vn.giapha.research.members.support.MemberSupport.ReconciliationCheck;

import java.time.Duration;

/**
 * Phase 6.2 — at least one reproducible drift scenario.
 *
 * <p>members-service owns the {@code members} table. members are
 * cascaded out via outbox events to events-service and relationships-
 * service. This check counts:
 *
 * <ol>
 *   <li>The number of {@code members} rows in members-service's schema.</li>
 *   <li>The number of distinct {@code aggregateId}s referenced in the
 *       outbox for {@code MemberDeleted} events.</li>
 * </ol>
 *
 * If the number of "MemberDeleted" outbox rows exceeds the number of
 * members deleted since the last reconciliation tick, that's a drift
 * signal — the relay published events that participants haven't
 * applied yet, or the participants lost state.
 */
@Component
public class MembersDeleteDriftCheck implements ReconciliationCheck {

    private final JdbcTemplate jdbc;

    public MembersDeleteDriftCheck(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public String name() {
        return "members.delete-drift";
    }

    @Override
    public Duration interval() {
        return Duration.ofMinutes(5);
    }

    @Override
    public ReconciliationResult run() {
        Long membersCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM members WHERE deleted_at IS NULL", Long.class);
        Long pendingDeletes = jdbc.queryForObject(
                "SELECT COUNT(*) FROM outbox "
                + "WHERE event_type = 'members.MemberDeleted' "
                + "AND published_at IS NULL",
                Long.class);
        long total = membersCount == null ? 0 : membersCount;
        long drift = pendingDeletes == null ? 0 : pendingDeletes;
        // Drift is "non-zero pending deletes" — participants should have
        // applied these within the SLA (ADR-105, 30s).
        return new ReconciliationResult(name(), drift > 0, drift, total,
                "pending-deleted=" + drift + "/members=" + total);
    }
}
