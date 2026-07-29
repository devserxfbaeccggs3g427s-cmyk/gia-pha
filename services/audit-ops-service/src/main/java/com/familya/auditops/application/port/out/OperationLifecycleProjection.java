package com.familya.auditops.application.port.out;

import com.familya.auditops.domain.model.OperationLifecycleRow;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface OperationLifecycleProjection {

    void upsertStarted(OperationLifecycleRow row, String lastEventId);

    void applyStateChange(OperationLifecycleRow row, Instant finalizedAt, String lastEventId);

    Optional<OperationLifecycleRow> find(UUID operationId);
}