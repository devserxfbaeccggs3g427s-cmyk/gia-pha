package com.familya.sharing.adapter.in.rest;

import com.familya.sharing.application.port.in.PublicLookupQuery;
import com.familya.sharing.application.usecase.ResolvePublicProjectionUseCase;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * REST controller phục vụ các truy vấn <b>công khai</b> (không yêu cầu xác thực)
 * tới sharing-service thông qua URL chia sẻ.
 * <p>
 * Hai endpoint được cung cấp:
 * <ul>
 *     <li>{@code GET /api/v2/public/sharing/{token}/media/{mediaId}} &mdash;
 *         tra cứu projection cho một media cụ thể.</li>
 *     <li>{@code GET /api/v2/public/sharing/{token}} &mdash; tra cứu projection
 *         cho toàn bộ phạm vi của liên kết.</li>
 * </ul>
 * Phản hồi chỉ chứa các trường đã được lọc trắng (allowlist) &mdash; mọi
 * trường khác đều bị tầng ghi từ chối.
 */
@RestController
@RequestMapping("/api/v2/public/sharing")
public class PublicSharingController {

    private final ResolvePublicProjectionUseCase lookup;

    /**
     * Khởi tạo controller.
     *
     * @param lookup use case tra cứu projection công khai.
     */
    public PublicSharingController(ResolvePublicProjectionUseCase lookup) {
        this.lookup = lookup;
    }

    /**
     * Tra cứu projection cho một media cụ thể bằng token.
     *
     * @param token   token chia sẻ (raw token).
     * @param mediaId định danh media cần truy vấn.
     * @return {@link ResponseEntity} chứa {@code Map<String,Object>} projection
     *         hoặc lỗi 404 nếu liên kết không hợp lệ/projection không khả dụng.
     */
    @GetMapping("/{token}/media/{mediaId}")
    public ResponseEntity<Map<String, Object>> media(@PathVariable String token, @PathVariable UUID mediaId) {
        // Sử dụng thời điểm hiện tại làm mốc kiểm tra hết hạn.
        var result = lookup.execute(new PublicLookupQuery(token, mediaId, Instant.now()));
        return ResponseEntity.ok(result.projection());
    }

    /**
     * Tra cứu projection cho toàn bộ phạm vi của liên kết.
     *
     * @param token token chia sẻ (raw token).
     * @return {@link ResponseEntity} chứa {@code Map<String,Object>} projection.
     */
    @GetMapping("/{token}")
    public ResponseEntity<Map<String, Object>> scope(@PathVariable String token) {
        // Truyền mediaId = null để use case rẽ nhánh sang đọc projection theo scope/targetId.
        var result = lookup.execute(new PublicLookupQuery(token, null, Instant.now()));
        return ResponseEntity.ok(result.projection());
    }
}