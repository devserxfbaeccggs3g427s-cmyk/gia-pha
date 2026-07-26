package vn.giapha.research.audit.application.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reconciliation report (Task 46). The per-tree report includes both
 * blocking discrepancies (must be zero to flip cutover) and nonblocking
 * differences (allowed by the approved reconciliation matrix).
 */
public record ReconciliationReport(
        String treeExternalId,
        long sourceCount,
        long acceptedCount,
        long quarantinedCount,
        long blockingDiscrepancies,
        long nonblockingDiscrepancies,
        boolean reconciled,
        List<Discrepancy> blocking,
        List<Discrepancy> nonblocking,
        String canonicalDigest) {

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("treeExternalId", treeExternalId);
        map.put("sourceCount", sourceCount);
        map.put("acceptedCount", acceptedCount);
        map.put("quarantinedCount", quarantinedCount);
        map.put("blocking", blockingDiscrepancies);
        map.put("nonblocking", nonblockingDiscrepancies);
        map.put("reconciled", reconciled);
        map.put("blockingDetails", blocking.stream().map(Discrepancy::toMap).toList());
        map.put("nonblockingDetails", nonblocking.stream().map(Discrepancy::toMap).toList());
        map.put("canonicalDigest", canonicalDigest);
        return map;
    }

    public record Discrepancy(String type, String description, String owner, String rule) {
        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("type", type);
            map.put("description", description);
            map.put("owner", owner);
            map.put("rule", rule);
            return map;
        }
    }
}
