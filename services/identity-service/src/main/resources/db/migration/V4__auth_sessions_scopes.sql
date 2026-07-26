-- ---------------------------------------------------------------------------
-- V4: Final Spring authentication support (Task 19.5).
--
-- Adds:
--   * auth_sessions.scopes — whitespace-separated authority scopes carried
--     over from the bridge token during cutover (tree, share, ops ...).
--   * auth_sessions.sweep_at — nullable timestamp used by the retention
--     sweep that purges revoked sessions past the audit window (Req 15.14).
-- ---------------------------------------------------------------------------

ALTER TABLE auth_sessions
    ADD COLUMN scopes VARCHAR(500) NOT NULL DEFAULT '';

ALTER TABLE auth_sessions
    ADD COLUMN sweep_at DATETIME(6) NULL AFTER revoke_reason;

CREATE INDEX ix_auth_sessions_sweep ON auth_sessions (sweep_at, revoked_at);
