ALTER TABLE delete_tree_saga_step
    ADD COLUMN next_attempt_at TIMESTAMP(6) NULL AFTER max_attempts,
    ADD COLUMN step_deadline_at TIMESTAMP(6) NULL AFTER last_dispatched_at,
    ADD COLUMN dispatch_token CHAR(36) NULL AFTER step_deadline_at,
    ADD COLUMN last_failure_at TIMESTAMP(6) NULL AFTER dispatch_token,
    ADD KEY ix_dt_step_retry (state, next_attempt_at),
    ADD KEY ix_dt_step_deadline (state, step_deadline_at);
