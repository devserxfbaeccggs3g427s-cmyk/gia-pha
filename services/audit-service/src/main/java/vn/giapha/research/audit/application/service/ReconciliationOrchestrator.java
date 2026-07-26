package vn.giapha.research.audit.application.service;

import java.util.List;

import vn.giapha.research.audit.application.contract.TransformedDataset;

/**
 * Reconciliation orchestrator (Task 46, Req 19.10-19.12). Compares the
 * transformed dataset against MySQL + Blob and produces a per-tree report
 * with blocking/nonblocking discrepancies. Cutover requires zero blocking
 * discrepancies; every nonblocking difference references a rule and an
 * owner.
 */
public interface ReconciliationOrchestrator {

    ReconciliationReport reconcile(String treeExternalId,
            TransformedDataset transformed);
}
