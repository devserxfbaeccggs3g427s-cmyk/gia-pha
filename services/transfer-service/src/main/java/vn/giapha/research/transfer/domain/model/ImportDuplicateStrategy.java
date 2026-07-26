package vn.giapha.research.transfer.domain.model;

/** Legacy duplicate-handling strategy for the import pipeline. */
public enum ImportDuplicateStrategy {
    SKIP,
    OVERWRITE,
    REGENERATE
}
