/**
 * Binary storage module.
 *
 * <p>Owns {@code upload_intents}, {@code file_cleanup_jobs} and {@code binary_replicas}. Exposes
 * the {@code BinaryObjectStore} port and implements it against the Vercel Blob control gateway
 * (ADR-005/ADR-006): exact-path signed URL issuance, upload intent lifecycle with quarantine, and
 * best-effort deletion via durable cleanup jobs (DEF-08 parity).
 *
 * <p>Layout follows the hexagonal convention from design.md: {@code domain}, {@code application}
 * (ports in/out, commands, services), {@code adapter.in.web}, {@code adapter.in.worker},
 * {@code adapter.out.mysql}, {@code adapter.out.http} and {@code config}.
 */
package vn.giapha.research.binarystorage;
