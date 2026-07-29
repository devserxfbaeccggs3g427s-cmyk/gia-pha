# Identity Credentials Rehash Runbook

Identity Service rehashes BCrypt on every successful login (ADR-005).
This runbook is invoked when an operator wants to force a bulk
rehash of all users (e.g. after a CVE in the legacy cost factor).

## Procedure

1. Set `familya.identity.bcrypt.cost` to the new factor.
2. Roll the identity service with the new cost; the
   `AuthenticateUseCase` rehashes on next successful login.
3. The `IdentityCredentialsRehashed` event is published; the audit
   projection records the rehash.
4. After 30 days, run a one-shot job that finds all users with a
   cost factor below the new minimum and forces a rehash on next
   authentication by setting a `force_rehash` flag.
5. The job is run by the migration service with a manual-review
   record in Audit & Operations.

## Rollback

Reverting the cost factor re-enables legacy hashes; the service
rehashes only on a successful login, so no login is blocked.
