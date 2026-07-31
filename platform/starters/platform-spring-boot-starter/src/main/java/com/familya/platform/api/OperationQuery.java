package com.familya.platform.api;

import java.util.Optional;
import java.util.UUID;

/**
 * Read port for the operation projection. The owning service provides
 * an adapter (see {@code platform/observability/OperationProjection.java}
 * in the audit-ops reference) so the shared
 * {@link OperationController} can answer polling requests uniformly.
 */
public interface OperationQuery {

    Optional<AsyncOperation> findById(UUID operationId);
}
