package com.familya.identity.application.port.in;

import java.util.UUID;

/**
 * Command cho use case {@code VerifyEmailUseCase}.
 *
 * <p>Command chứa:
 * <ul>
 *     <li>{@code userId} – UUID người dùng cần xác minh.</li>
 *     <li>{@code token}  – mã xác minh được gửi qua email.</li>
 * </ul>
 *
 * <p>Use case sẽ kiểm tra sự khớp giữa {@code userId} và token, đồng
 * thời đảm bảo token còn hiệu lực và chưa được sử dụng trước khi đánh
 * dấu người dùng đã xác minh.
 *
 * @param userId UUID của người dùng cần xác minh.
 * @param token  chuỗi token xác minh email.
 */
public record VerifyEmailCommand(UUID userId, String token) { }
