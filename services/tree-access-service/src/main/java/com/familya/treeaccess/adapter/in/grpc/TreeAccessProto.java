package com.familya.treeaccess.adapter.in.grpc;

/**
 * Generated-by-protobuf-equivalent POJO for TreeAccessLookup service
 * messages. In a CI environment, the {@code contracts/grpc/treeaccess}
 * Protobuf files are compiled by the {@code protobuf-maven-plugin}
 * and these classes are replaced by the generated source. The public
 * surface used by {@link TreeAccessLookupService} is identical so the
 * generated source can be substituted without code changes.
 */
public final class TreeAccessProto {
    private TreeAccessProto() { }

    public static final class AuthorizeRequest {
        private String treeId = "";
        private String userId = "";
        private long expectedRevision = 0L;

        public String getTreeId() { return treeId; }
        public String getUserId() { return userId; }
        public long getExpectedRevision() { return expectedRevision; }

        public static Builder newBuilder() { return new Builder(); }
        public static final class Builder {
            private final AuthorizeRequest r = new AuthorizeRequest();
            public Builder setTreeId(String v) { r.treeId = v; return this; }
            public Builder setUserId(String v) { r.userId = v; return this; }
            public Builder setExpectedRevision(long v) { r.expectedRevision = v; return this; }
            public AuthorizeRequest build() { return r; }
        }
    }

    public static final class AuthorizeResponse {
        public enum Outcome { UNKNOWN, AUTHORIZED, DENIED, NOT_FOUND, STALE }
        private Outcome outcome = Outcome.UNKNOWN;
        private String role = "";
        private boolean canEdit;
        private boolean canView;
        private long revision;
        private long epoch;
        private String traceId = "";

        public Outcome getOutcome() { return outcome; }
        public String getRole() { return role; }
        public boolean getCanEdit() { return canEdit; }
        public boolean getCanView() { return canView; }
        public long getRevision() { return revision; }
        public long getEpoch() { return epoch; }
        public String getTraceId() { return traceId; }

        public static Builder newBuilder() { return new Builder(); }
        public static final class Builder {
            private final AuthorizeResponse r = new AuthorizeResponse();
            public Builder setOutcome(Outcome v) { r.outcome = v; return this; }
            public Builder setRole(String v) { r.role = v == null ? "" : v; return this; }
            public Builder setCanEdit(boolean v) { r.canEdit = v; return this; }
            public Builder setCanView(boolean v) { r.canView = v; return this; }
            public Builder setRevision(long v) { r.revision = v; return this; }
            public Builder setEpoch(long v) { r.epoch = v; return this; }
            public Builder setTraceId(String v) { r.traceId = v == null ? "" : v; return this; }
            public AuthorizeResponse build() { return r; }
        }
    }

    public static final class GetTreeRevisionRequest {
        private String treeId = "";
        public String getTreeId() { return treeId; }
        public static Builder newBuilder() { return new Builder(); }
        public static final class Builder {
            private final GetTreeRevisionRequest r = new GetTreeRevisionRequest();
            public Builder setTreeId(String v) { r.treeId = v; return this; }
            public GetTreeRevisionRequest build() { return r; }
        }
    }

    public static final class GetTreeRevisionResponse {
        private boolean found;
        private long revision;
        private long epoch;
        private String state = "";
        public boolean getFound() { return found; }
        public long getRevision() { return revision; }
        public long getEpoch() { return epoch; }
        public String getState() { return state; }
        public static Builder newBuilder() { return new Builder(); }
        public static final class Builder {
            private final GetTreeRevisionResponse r = new GetTreeRevisionResponse();
            public Builder setFound(boolean v) { r.found = v; return this; }
            public Builder setRevision(long v) { r.revision = v; return this; }
            public Builder setEpoch(long v) { r.epoch = v; return this; }
            public Builder setState(String v) { r.state = v == null ? "" : v; return this; }
            public GetTreeRevisionResponse build() { return r; }
        }
    }
}