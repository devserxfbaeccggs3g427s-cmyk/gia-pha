package vn.giapha.research.binarystorage.domain.model;

/**
 * Data-plane operations a signed URL capability can be scoped to. Wire values
 * match the blob control gateway contract (gateway/blob-gateway README).
 */
public enum BlobOperation {

    GET("get"),
    HEAD("head"),
    PUT("put"),
    DELETE("delete");

    private final String wireValue;

    BlobOperation(String wireValue) {
        this.wireValue = wireValue;
    }

    /** Lowercase operation name used in gateway requests. */
    public String wireValue() {
        return wireValue;
    }
}
