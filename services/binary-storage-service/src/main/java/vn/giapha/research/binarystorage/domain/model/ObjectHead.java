package vn.giapha.research.binarystorage.domain.model;

/**
 * Object metadata observed through a signed HEAD (upload verification step:
 * design.md §Upload Flow "Verify and stream object").
 *
 * @param sizeBytes   Content-Length reported by the store
 * @param contentType Content-Type reported by the store (may be null)
 * @param etag        strong ETag for conditional deletes (may be null)
 */
public record ObjectHead(long sizeBytes, String contentType, String etag) {
}
