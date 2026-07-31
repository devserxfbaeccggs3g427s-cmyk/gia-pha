package com.familya.media.application.port.out;

import java.io.InputStream;
import java.util.UUID;

/**
 * Produces a 480x480 WebP thumbnail for a promoted media asset. The
 * generator returns a stream to be uploaded via a fresh
 * capability-issuer call; the stream itself is not logged.
 */
public interface ThumbnailGenerator {

    ThumbnailResult generate(UUID mediaId, String mimeType, long byteSize);

    record ThumbnailResult(int width, int height, String mime, InputStream payload) { }
}
