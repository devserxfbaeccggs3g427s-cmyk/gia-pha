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
 * Default thumbnail generator. Placeholder implementation emits a
 * deterministic 1x1 WebP stub with the configured dimensions; the
 * real ImageIO-based 480x480 lossy WebP encoder lands in a separate
 * module. The use case is best-effort: a thumbnail failure is
 * logged and the promotion path still succeeds.
 */
@Component
public class DefaultThumbnailGenerator implements ThumbnailGenerator {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultThumbnailGenerator.class);

    @SuppressWarnings("unused")
    private final boolean enabled;

    public DefaultThumbnailGenerator(@Value("${familya.media.thumbnail.enabled:false}") boolean enabled) {
        this.enabled = enabled;
    }

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
