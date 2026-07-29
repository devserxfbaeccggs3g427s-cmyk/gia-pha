package com.familya.identity.adapter.in.grpc;

import io.grpc.stub.StreamObserver;

/**
 * Stand-in for the generated gRPC service base. In a CI environment,
 * the {@code contracts/grpc/identity/IdentityLookup.proto} file is
 * compiled by {@code protobuf-maven-plugin} which produces
 * {@code IdentityLookupGrpc.IdentityLookupImplBase}. CI replaces this
 * file with the generated source; the public method signatures are
 * identical.
 */
public final class IdentityLookupGrpc {
    private IdentityLookupGrpc() { }

    public static abstract class IdentityLookupImplBase implements io.grpc.BindableService {
        public void lookup(IdentityLookupRequest request,
                           StreamObserver<IdentityLookupResponse> responseObserver) {
            throw new UnsupportedOperationException("lookup() must be overridden");
        }

        @Override
        public io.grpc.ServerServiceDefinition bindService() {
            return io.grpc.ServerServiceDefinition.builder("familya.identity.v1.IdentityLookup").build();
        }
    }
}
