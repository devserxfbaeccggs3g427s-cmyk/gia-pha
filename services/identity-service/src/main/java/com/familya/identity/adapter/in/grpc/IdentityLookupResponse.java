package com.familya.identity.adapter.in.grpc;

/**
 * Stand-in for the generated IdentityLookupResponse message. In a CI
 * environment, the {@code contracts/grpc/identity/IdentityLookup.proto}
 * file is compiled by {@code protobuf-maven-plugin} which produces
 * identical source. The public method signatures are unchanged.
 */
public final class IdentityLookupResponse {
    private String userId = "";
    private boolean found;

    public String getUserId() { return userId; }
    public boolean getFound() { return found; }

    public static Builder newBuilder() { return new Builder(); }
    public static final class Builder {
        private final IdentityLookupResponse r = new IdentityLookupResponse();
        public Builder setUserId(String v) { r.userId = v == null ? "" : v; return this; }
        public Builder setFound(boolean v) { r.found = v; return this; }
        public IdentityLookupResponse build() { return r; }
    }
}
