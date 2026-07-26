/**
 * Audit and operations module.
 *
 * <p>Owns {@code audit_logs}, {@code security_audit_logs}, {@code processed_commands}
 * (idempotency) and {@code outbox_events}. Provides the transactional outbox (ADR-008), durable
 * worker leasing with backoff, and append-only audit trails consumed by every other module
 * through application ports.
 *
 * <p>Layout follows the hexagonal convention from design.md: {@code domain}, {@code application}
 * (ports in/out, services), {@code adapter.in.worker}, {@code adapter.out.mysql} and
 * {@code config}.
 */
package vn.giapha.research.audit;
