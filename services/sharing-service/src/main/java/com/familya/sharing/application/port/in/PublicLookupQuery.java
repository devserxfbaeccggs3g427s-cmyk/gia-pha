package com.familya.sharing.application.port.in;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Truy vấn đầu vào phục vụ tra cứu một projection công khai dựa trên token chia sẻ.
 * <p>
 * Truy vấn này được sử dụng bởi {@code ResolvePublicProjectionUseCase} để trả
 * lời các yêu cầu HTTP công khai (không cần xác thực) truy cập vào dữ liệu đã
 * được "allowlist" (lọc trắng) cho phép công khai.
 *
 * @param token   chuỗi token thô (raw token) do người dùng cuối cung cấp
 *                &mdash; sẽ được băm SHA-256 trước khi tra cứu.
 * @param mediaId định danh media cụ thể cần truy vấn (có thể {@code null} khi
 *                truy vấn phạm vi rộng hơn).
 * @param now     thời điểm tham chiếu dùng để kiểm tra hết hạn của liên kết.
 */
public record PublicLookupQuery(
        String token,
        UUID mediaId,
        Instant now) {
}