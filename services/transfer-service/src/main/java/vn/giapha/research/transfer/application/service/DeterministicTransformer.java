package vn.giapha.research.transfer.application.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import vn.giapha.research.transfer.application.contract.ExtractedDataset;

/**
 * Deterministic transformation of an immutable extraction into the
 * relational pipeline (Task 45, Req 19.5-19.9). Preserves external IDs
 * and timestamps, maps surrogate keys, and quarantines invalid records
 * with an explicit reason so the operator can replay a single row without
 * re-extracting.
 */
public interface DeterministicTransformer {

    Result transform(ExtractedDataset extracted);

    record Result(
            List<Transformed> transformed,
            List<Quarantined> quarantined,
            List<String> duplicateWarnings,
            String canonicalDigest) {}

    record Transformed(
            String aggregateType,
            String externalId,
            Long treeKey,
            Map<String, Object> payload) {}

    record Quarantined(String source, String reason, Map<String, Object> row) {}
}
