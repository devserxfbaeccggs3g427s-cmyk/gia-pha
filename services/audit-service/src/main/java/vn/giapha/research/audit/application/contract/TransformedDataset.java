package vn.giapha.research.audit.application.contract;

import java.util.List;
import java.util.Map;

public record TransformedDataset(
        List<TransformedRecord> transformed,
        List<QuarantinedRecord> quarantined,
        List<String> duplicateWarnings,
        String canonicalDigest) {

    public record TransformedRecord(String aggregateType, String externalId,
            Long treeKey, Map<String, Object> payload) {
    }

    public record QuarantinedRecord(String source, String reason, Map<String, Object> row) {
    }
}
