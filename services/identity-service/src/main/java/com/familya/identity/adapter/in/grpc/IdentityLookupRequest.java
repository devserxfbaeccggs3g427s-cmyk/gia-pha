package com.familya.identity.adapter.in.grpc;

/**
 * Stand-in for the generated IdentityLookupRequest message. In a CI
 * environment, the {@code contracts/grpc/identity/IdentityLookup.proto}
 * file is compiled by {@code protobuf-maven-plugin} which produces
 * identical source. The public method signatures are unchanged.
 */
public final class IdentityLookupRequest {
    private String userId = "";

    public String getUserId() { return userId; }

    public static Builder newBuilder() { return new Builder(); }
    public static final class Builder {
        private final IdentityLookupRequest r = new IdentityLookupRequest();
        public Builder setUserId(String v) { r.userId = v == null ? "" : v; return this; }
        public IdentityLookupRequest build() { return r; }
    }
}
