package com.familya.identity.application.port.in;

import java.util.UUID;

/**
 * Command cho use case {@code RevokeSessionUseCase}.
 *
 * <p>Command chứa đầy đủ thông tin cần thiết để thực hiện thu hồi phiên
 * một cách an toàn – đặc biệt là {@code actingUserId} giúp use case
 * kiểm tra quyền sở hữu phiên, tránh tình trạng người dùng A thu hồi
 * phiên của người dùng B.
 *
 * @param sessionId     UUID của phiên cần thu hồi.
 * @param actingUserId  UUID của người dùng đang thực hiện thao tác.
 */
public record RevokeSessionCommand(UUID sessionId, UUID actingUserId) { }
