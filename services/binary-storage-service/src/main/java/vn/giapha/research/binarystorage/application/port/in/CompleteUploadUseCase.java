package vn.giapha.research.binarystorage.application.port.in;

/**
 * Drives the verification → validation → scan → promotion pipeline for one
 * upload intent (design.md §Upload Flow steps 4-8). Invoked by the gateway
 * completion callback and re-driven by reconciliation for intents stuck in
 * {@code UPLOADED}; the whole pipeline is idempotent, so redelivery and
 * crash-retry are safe at every step.
 */
public interface CompleteUploadUseCase {

    /**
     * @param intentExternalId the opaque {@code callbackPayload} echoed by the
     *                         store's upload-completed event
     * @throws vn.giapha.research.binarystorage.domain.error.BinaryStorageException with a
     *         retryable reason when a dependency (store, scanner) is down — the
     *         caller should surface a 5xx so the event is redelivered; terminal
     *         rejections mark the intent {@code FAILED} and do not throw
     */
    void handleCompletion(String intentExternalId);
}
