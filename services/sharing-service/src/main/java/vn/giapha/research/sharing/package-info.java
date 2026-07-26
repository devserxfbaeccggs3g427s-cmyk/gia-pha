/**
 * Sharing module.
 *
 * <p>Owns {@code share_links}. Issues signed share tokens, enforces expiry/revocation and serves
 * the public read-only projection through an explicit allowlist DTO (ADR-014) — the single
 * deliberate compatibility break with the legacy full-member response (DEF-01), guarded by the
 * {@code compat.share.full-projection} flag.
 *
 * <p>Layout follows the hexagonal convention from design.md: {@code domain}, {@code application}
 * (ports in/out, queries, services), {@code adapter.in.web}, {@code adapter.out.mysql} and
 * {@code config}.
 */
package vn.giapha.research.sharing;
