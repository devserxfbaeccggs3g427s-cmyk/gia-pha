package vn.giapha.research.binarystorage.domain.model;

import java.util.Objects;

/**
 * Full object payload fetched through a signed GET. Sized for this system's
 * frozen caps (media ≤ 10 MiB, imports ≤ 25 MiB), so a byte array is simpler
 * and safer than exposing a stream tied to a connection lifecycle.
 */
public record BinaryContent(byte[] bytes, String contentType, String etag) {

    public BinaryContent {
        Objects.requireNonNull(bytes, "bytes");
    }

    public long sizeBytes() {
        return bytes.length;
    }

    /** Redacted by design: renders without the payload. */
    @Override
    public String toString() {
        return "BinaryContent[sizeBytes=" + bytes.length + ", contentType=" + contentType + "]";
    }
}
