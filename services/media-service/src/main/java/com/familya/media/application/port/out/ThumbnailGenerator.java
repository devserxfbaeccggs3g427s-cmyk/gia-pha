package com.familya.media.application.port.out;

import java.io.InputStream;
import java.util.UUID;

/**
 * Produces a 480x480 WebP thumbnail for a promoted media asset. The
 * generator returns a stream to be uploaded via a fresh
 * capability-issuer call; the stream itself is not logged.
 *
 * <p>Port ra (driven port) của kiến trúc hexagonal: tạo thumbnail cho
 * media sau khi được promote sang {@code READY}.</p>
 *
 * <p>Kích thước 480x480 và định dạng WebP là hằng số thiết kế để cân
 * bằng giữa chất lượng hiển thị và dung lượng lưu trữ. Khi thay đổi
 * cần đồng bộ với client.</p>
 */
public interface ThumbnailGenerator {

    /**
     * Sinh thumbnail cho một media.
     *
     * @param mediaId  UUID media (audit / logging).
     * @param mimeType MIME type gốc; thumbnail generator dùng để chọn
     *                 codec nguồn.
     * @param byteSize kích thước bytes gốc; cho phép generator quyết
     *                 định có nên tạo thumbnail hay trả lỗi.
     * @return {@link ThumbnailResult} chứa stream dữ liệu WebP; caller
     *         sẽ upload qua {@code BlobCapabilityIssuer}. Không log
     *         payload.
     */
    ThumbnailResult generate(UUID mediaId, String mimeType, long byteSize);

    /**
     * Kết quả sinh thumbnail.
     *
     * @param width   chiều rộng (pixel), mặc định {@code 480}.
     * @param height  chiều cao (pixel), mặc định {@code 480}.
     * @param mime    MIME type của thumbnail (thường {@code image/webp}).
     * @param payload stream bytes của thumbnail; caller đóng stream khi
     *                upload xong.
     */
    record ThumbnailResult(int width, int height, String mime, InputStream payload) { }
}
