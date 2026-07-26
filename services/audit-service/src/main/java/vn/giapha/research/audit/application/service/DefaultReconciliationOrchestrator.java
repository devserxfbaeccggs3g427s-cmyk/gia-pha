package vn.giapha.research.audit.application.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import vn.giapha.research.audit.application.contract.TransformedDataset;

/**
 * Default reconciliation orchestrator (Task 46). The placeholder
 * implementation returns a reconciled report when no discrepancies are
 * found; the production wiring feeds MySQL/Blob counts into the same
 * shape so the cutover gating reads from one source of truth.
 */
@Service
public class DefaultReconciliationOrchestrator implements ReconciliationOrchestrator {

    @Override
    public ReconciliationReport reconcile(String treeExternalId,
            TransformedDataset transformed) {
        long blocking = transformed.quarantined().stream()
                .filter(q -> "MISSING_EXTERNAL_ID".equals(q.reason())
                        || "RAW_MISSING".equals(q.reason()))
                .count();
        long nonblocking = transformed.quarantined().size() - blocking;
        return new ReconciliationReport(
                treeExternalId,
                transformed.transformed().size(),
                transformed.transformed().size() - transformed.quarantined().size(),
                transformed.quarantined().size(),
                blocking,
                nonblocking,
                blocking == 0,
                List.of(),
                List.of(),
                transformed.canonicalDigest());
    }
}
