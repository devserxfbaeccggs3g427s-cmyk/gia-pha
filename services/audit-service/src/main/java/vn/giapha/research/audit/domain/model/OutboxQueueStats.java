package vn.giapha.research.audit.domain.model;

/**
 * Point-in-time queue health for metrics/alerting (Task 12.5): ready backlog,
 * events under lease, dead letters and the age of the oldest ready event.
 */
public record OutboxQueueStats(
        long readyCount,
        long inProgressCount,
        long deadCount,
        long oldestReadyAgeSeconds) {
}
