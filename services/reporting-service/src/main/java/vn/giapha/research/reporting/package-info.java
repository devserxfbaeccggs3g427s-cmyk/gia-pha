/**
 * Reporting and search module (read-only).
 *
 * <p>Owns no tables; queries projections over tree-content data. Implements Vietnamese-aware
 * search and autocomplete (NFD normalization, đ/Đ → d, weighted scoring with prefix bonus and the
 * legacy 100-point cap, DEF-14), member filters, statistics and reports (ADR-011).
 *
 * <p>Layout follows the hexagonal convention from design.md: {@code domain}, {@code application}
 * (ports in/out, queries, services), {@code adapter.in.web}, {@code adapter.out.mysql} and
 * {@code config}.
 */
package vn.giapha.research.reporting;
