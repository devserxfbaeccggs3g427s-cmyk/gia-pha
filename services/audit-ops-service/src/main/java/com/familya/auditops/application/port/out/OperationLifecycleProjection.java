/**
 * Port ghi/đọc cho projection vòng đời operation. Implementation nằm trong
 * {@code JdbcOperationLifecycleProjection}. Bounded context sở hữu Saga vẫn
 * là nguồn dữ liệu sự thật; projection này chỉ phục vụ operator UI và
 * truy vết theo ADR-003.
 */
package com.familya.auditops.application.port.out;

import com.familya.auditops.domain.model.OperationLifecycleRow;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface OperationLifecycleProjection {

    void upsertStarted(OperationLifecycleRow row, String lastEventId);

    void applyStateChange(OperationLifecycleRow row, Instant finalizedAt, String lastEventId);

    Optional<OperationLifecycleRow> find(UUID operationId);

    java.util.List<OperationLifecycleRow> findByState(String state, int limit);

    java.util.List<OperationLifecycleRow> findByOwnerService(String ownerService, int limit);

    long countByState(String state);

    long countByOwnerServiceAndState(String ownerService, String state);

    java.util.List<OperationLifecycleRow> findStale(Instant olderThan, int limit);

    java.util.List<Watermark> listWatermarks();

    record Watermark(String topic, long lastOffset, Instant lastSeenAt) { }
}
