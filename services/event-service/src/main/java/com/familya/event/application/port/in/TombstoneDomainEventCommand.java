package com.familya.event.application.port.in;

import java.util.UUID;

/**
 * Lệnh tombstone (xóa mềm) một
 * {@link com.familya.event.domain.model.DomainEvent sự kiện gia phả}.
 *
 * <p>Sau khi tombstone, sự kiện vẫn tồn tại trong cơ sở dữ liệu nhưng
 * được đánh dấu {@code tombstoned_at}. Use case thực hiện lệnh này là
 * {@link com.familya.event.application.usecase.TombstoneDomainEventUseCase}.
 *
 * @param eventId               định danh sự kiện cần tombstone.
 * @param actingUser            người dùng thực hiện.
 * @param expectedVersion       phiên bản aggregate kỳ vọng (If-Match).
 * @param expectedTreeRevision  revision cây kỳ vọng cho phân quyền.
 *
 * @author gia-pha platform
 */
public record TombstoneDomainEventCommand(UUID eventId, UUID actingUser, long expectedVersion, long expectedTreeRevision) { }
