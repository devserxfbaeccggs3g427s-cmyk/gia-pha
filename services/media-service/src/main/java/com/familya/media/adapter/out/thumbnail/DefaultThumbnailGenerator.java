package com.familya.media.adapter.out.thumbnail;

import com.familya.media.application.port.out.ThumbnailGenerator;
import com.familya.media.domain.policy.ThumbnailPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.util.UUID;

/**
 * Adapter đầu ra (outbound) — generator thumbnail mặc định.
 * <p>
 * Placeholder phát ra một stub WebP 1×1 byte (deterministic). Implementation
 * thật dựa trên ImageIO 480×480 lossy WebP sẽ ở một module riêng. Đường
 * thumbnail là best-effort: lỗi chỉ được log, đường promote vẫn tiếp tục
 * thành công.
 */
@Component
public class DefaultThumbnailGenerator implements ThumbnailGenerator {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultThumbnailGenerator.class);

    @SuppressWarnings("unused")
    private final boolean enabled;

    /**
     * Khởi tạo generator.
     *
     * @param enabled cờ bật/tắt, mặc định false (chỉ sinh stub). Đọc từ
     *                {@code familya.media.thumbnail.enabled}.
     */
    public DefaultThumbnailGenerator(@Value("${familya.media.thumbnail.enabled:false}") boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Sinh thumbnail cho một media.
     * <p>
     * Hiện tại chỉ trả về stub 1 byte (placeholder). Best-effort — use case sẽ
     * log lỗi nếu cần nhưng không chặn promotion.
     *
     * @param mediaId  UUID media.
     * @param mimeType MIME type nguồn.
     * @param byteSize kích thước nguồn.
     * @return {@link ThumbnailResult} với kích thước/MIME từ {@link ThumbnailPolicy}.
     */
    @Override
    public ThumbnailResult generate(UUID mediaId, String mimeType, long byteSize) {
        if (!enabled) {
            LOG.info("Thumbnail generation skipped mediaId={} (disabled)", mediaId);
            return new ThumbnailResult(ThumbnailPolicy.WIDTH, ThumbnailPolicy.HEIGHT,
                    ThumbnailPolicy.MIME, new ByteArrayInputStream(new byte[]{ 0x1 }));
        }
        LOG.info("Thumbnail generation pending implementation mediaId={} srcMime={}", mediaId, mimeType);
        return new ThumbnailResult(ThumbnailPolicy.WIDTH, ThumbnailPolicy.HEIGHT,
                ThumbnailPolicy.MIME, new ByteArrayInputStream(new byte[]{ 0x1 }));
    }
}
