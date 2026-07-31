-- V6__operation_audit_full.sql — extends operation_audit with the full column set
-- required by Task 13.1 (stable state, result, allowlisted error, watermarks,
-- updatedAt). Members and Tree Access own their operation_audit rows; they
-- are written in the same local transaction as the Saga state and the outbox
-- row, and are read by OperationProjectionAdapter to answer polling. The
-- version column enables optimistic concurrency; update paths use
-- UPDATE ... WHERE id = ? AND version = ?.

ALTER TABLE operation_audit
    ADD COLUMN service VARCHAR(64) NULL AFTER correlation_id,
    ADD COLUMN operation_type VARCHAR(128) NULL AFTER service,
    ADD COLUMN target_revision BIGINT NULL AFTER operation_type,
    ADD COLUMN target_epoch BIGINT NULL AFTER target_revision,
    ADD COLUMN aggregate_type VARCHAR(64) NULL AFTER target_epoch,
    ADD COLUMN aggregate_id VARCHAR(64) NULL AFTER aggregate_type,
    ADD COLUMN tree_id CHAR(36) NULL AFTER aggregate_id,
    ADD COLUMN acting_user CHAR(36) NULL AFTER tree_id,
    ADD COLUMN error_code VARCHAR(128) NULL AFTER acting_user,
    ADD COLUMN error_message VARCHAR(2048) NULL AFTER error_code,
    ADD COLUMN updated_at TIMESTAMP(6) NULL AFTER started_at,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0 AFTER finished_at,
    ADD COLUMN failure_routing VARCHAR(32) NULL AFTER error_message,
    ADD COLUMN target_applied_revision BIGINT NULL AFTER failure_routing,
    ADD COLUMN target_applied_epoch BIGINT NULL AFTER target_applied_revision;

UPDATE operation_audit
   SET service = COALESCE(service, 'tree-access-service'),
       operation_type = COALESCE(operation_type, 'legacy.delete-tree'),
       updated_at = COALESCE(updated_at, started_at)
 WHERE service IS NULL OR operation_type IS NULL OR updated_at IS NULL;

CREATE INDEX ix_operation_audit_status_updated ON operation_audit (status, updated_at);
CREATE INDEX ix_operation_audit_tree ON operation_audit (tree_id, updated_at);
