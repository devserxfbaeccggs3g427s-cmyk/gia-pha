package com.familya.sharing.application.usecase;

import com.familya.sharing.application.port.in.PublicLookupQuery;
import com.familya.sharing.application.port.out.AllowlistedProjectionRepository;
import com.familya.sharing.application.port.out.AllowlistedProjectionRepository.ShareScope;
import com.familya.sharing.application.port.out.ShareLinkRepository;
import com.familya.sharing.domain.exception.ShareNotFoundException;
import com.familya.sharing.domain.model.ShareLink;
import com.familya.platform.telemetry.PlatformMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * Use case phục vụ tra cứu đồng bộ (synchronous) một projection công khai dựa
 * trên {@code token} và tùy chọn {@code mediaId}.
 * <p>
 * Các trường hợp token không hợp lệ (không tồn tại, đã thu hồi, đã hết hạn)
 * sẽ đều ném {@link ShareNotFoundException} &mdash; đảm bảo không rò rỉ
 * thông tin phân biệt giữa các nguyên nhân. Phản hồi chỉ chứa các trường đã
 * được allowlist; mọi khoá ngoài danh sách cho phép sẽ bị tầng ghi từ chối.
 *
 * @author gia-pha platform team
 */
@Service
public class ResolvePublicProjectionUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(ResolvePublicProjectionUseCase.class);

    private final ShareLinkRepository links;
    private final AllowlistedProjectionRepository projections;
    private final PlatformMetrics metrics;

    /**
     * Khởi tạo use case với các phụ thuộc.
     *
     * @param links       kho lưu trữ liên kết chia sẻ.
     * @param projections kho lưu trữ projection allowlist.
     * @param metrics     bộ thu thập metric.
     */
    public ResolvePublicProjectionUseCase(ShareLinkRepository links, AllowlistedProjectionRepository projections,
                                          PlatformMetrics metrics) {
        this.links = links;
        this.projections = projections;
        this.metrics = metrics;
    }

    /**
     * Thực thi tra cứu projection công khai.
     *
     * @param q truy vấn {@link PublicLookupQuery}.
     * @return {@link Result} chứa thông tin liên kết và projection tương ứng.
     * @throws ShareNotFoundException nếu token không hợp lệ, đã thu hồi, đã hết hạn
     *         hoặc projection không khả dụng.
     */
    @Transactional(readOnly = true)
    public Result execute(PublicLookupQuery q) {
        // Bước 1: Băm token để tra cứu &mdash; chỉ hash được lưu trong DB.
        String hash = CreateShareLinkUseCase.sha256Hex(q.token());

        // Bước 2: Tìm liên kết; nếu không tồn tại thì trả về lỗi "không rõ" để tránh rò rỉ.
        ShareLink link = links.findByTokenHash(hash)
                .orElseThrow(() -> new ShareNotFoundException("share.unknown"));

        // Bước 3: Kiểm tra thu hồi.
        if (link.revokedAt() != null) {
            metrics.mutationFailed("sharing-service", "publicLookup", "share.revoked");
            throw new ShareNotFoundException("share.revoked");
        }

        // Bước 4: Kiểm tra hết hạn so với thời điểm tham chiếu.
        if (link.expiresAt() != null && link.expiresAt().isBefore(q.now())) {
            metrics.mutationFailed("sharing-service", "publicLookup", "share.expired");
            throw new ShareNotFoundException("share.expired");
        }

        // Bước 5: Với phạm vi MEDIA và có mediaId, truy vấn projection cho media cụ thể.
        if (link.scope() == ShareLink.Scope.MEDIA && q.mediaId() != null) {
            ShareScope scope = ShareScope.MEDIA;
            Map<String, Object> projection = projections.readPublicProjection(link.treeId(), scope, q.mediaId());
            if (projection == null || projection.isEmpty()) {
                throw new ShareNotFoundException("media.unavailable");
            }
            return new Result(link.id(), link.scope(), link.role(), projection);
        }

        // Bước 6: Mặc định &mdash; đọc projection theo scope và targetId đã đăng ký trong liên kết.
        Map<String, Object> projection = projections.readPublicProjection(link.treeId(),
                ShareScope.valueOf(link.scope().name()), link.targetId());
        if (projection == null) {
            throw new ShareNotFoundException("scope.unavailable");
        }

        LOG.info("Public lookup shareId={} tree={} scope={}", link.id(), link.treeId(), link.scope());
        return new Result(link.id(), link.scope(), link.role(), projection);
    }

    /**
     * Kết quả tra cứu projection công khai.
     *
     * @param shareId    định danh liên kết đã dùng.
     * @param scope      phạm vi chia sẻ của liên kết.
     * @param role       vai trò được cấp qua liên kết.
     * @param projection {@code Map} các trường công khai (chỉ chứa các trường allowlist).
     */
    public record Result(UUID shareId, ShareLink.Scope scope, ShareLink.Role role, Map<String, Object> projection) { }
}