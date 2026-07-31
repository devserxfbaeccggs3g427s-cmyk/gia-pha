package com.familya.media.application.port.out;

import java.util.UUID;

/**
 * Blob capability issuer. Implementations must never log or persist
 * the resulting {@code signedPutUrl} / {@code signedGetUrl}; they are
 * bearer secrets returned once to the caller and discarded.
 *
 * <p>Port ra (driven port) của kiến trúc hexagonal: cấp các URL có chữ
 * ký (signed PUT/GET) cho phép client upload / download trực tiếp tới
 * blob store mà không cần đi qua media-service. Các URL là "bearer
 * secret": cấp đúng một lần, không log, không persist.</p>
 *
 * <p>Hợp đồng bảo mật:</p>
 * <ul>
 *   <li>Triển khai KHÔNG được ghi log hoặc lưu trữ {@code signedPutUrl} /
 *       {@code signedGetUrl}.</li>
 *   <li>URL hết hạn theo {@link Capability#expiresAtEpochMs()}; sau thời
 *       điểm đó caller phải yêu cầu lại (và bị authorize lại).</li>
 *   <li>{@link #invalidate(UUID)} được gọi khi media bị tombstone / xóa
 *       để vô hiệu hóa URL đang còn hiệu lực.</li>
 * </ul>
 */
public interface BlobCapabilityIssuer {

    /**
     * Cấp một capability (URL upload + URL download) cho một media cụ thể.
     *
     * @param treeId   UUID family-tree sở hữu media.
     * @param mediaId  UUID media cần cấp quyền truy cập.
     * @param exactPath đường dẫn "exact-path" xác định trước trong blob
     *                 store; URL ký sẽ bị giới hạn đúng path này.
     * @param mimeType MIME type dự kiến; dùng để thiết lập content-type
     *                 ràng buộc khi upload (vd. từ chối upload nếu khác).
     * @return {@link Capability} chứa URL upload, URL download và
     *         deadline hiệu lực (epoch millis).
     */
    Capability issue(UUID treeId, UUID mediaId, String exactPath, String mimeType);

    /**
     * Vô hiệu hóa mọi capability còn hiệu lực của media.
     *
     * @param mediaId UUID media cần thu hồi quyền truy cập.
     */
    void invalidate(UUID mediaId);

    /**
     * Capability do {@link #issue(UUID, UUID, String, String)} trả về.
     *
     * @param exactPath        đường dẫn exact-path trong blob store.
     * @param signedPutUrl     URL upload có chữ ký; bearer secret, KHÔNG
     *                         log/persist.
     * @param signedGetUrl     URL download có chữ ký; bearer secret,
     *                         KHÔNG log/persist.
     * @param expiresAtEpochMs deadline hiệu lực tính bằng epoch millis.
     */
    record Capability(String exactPath, String signedPutUrl, String signedGetUrl,
                      long expiresAtEpochMs) { }
}
