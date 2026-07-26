/**
 * Transfer module.
 *
 * <p>Owns {@code import_jobs}, {@code generated_artifact_jobs} and {@code tree_snapshots}.
 * Implements import preview/execute with strict validation parity (25 MiB cap, DEF-17), async
 * export/artifact generation, and user-facing tree snapshots with 30-day retention and restore.
 *
 * <p>Layout follows the hexagonal convention from design.md: {@code domain}, {@code application}
 * (ports in/out, commands, services), {@code adapter.in.web}, {@code adapter.in.worker},
 * {@code adapter.out.mysql} and {@code config}.
 */
package vn.giapha.research.transfer;
