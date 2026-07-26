/**
 * Identity and access module.
 *
 * <p>Owns {@code users}, {@code oauth_accounts}, {@code verification_tokens} and
 * {@code auth_sessions}. Implements credential auth (bcrypt cost 12, lockout 5 failures / 15
 * minutes, dummy-hash timing defense), OAuth account linking, email verification and the NextAuth
 * bridge-token validation used during the strangler migration (ADR-009).
 *
 * <p>Layout follows the hexagonal convention from design.md: {@code domain}, {@code application}
 * (ports in/out, commands, queries, services), {@code adapter.in.web}, {@code adapter.out.mysql}
 * and {@code config}.
 */
package vn.giapha.research.identity;
