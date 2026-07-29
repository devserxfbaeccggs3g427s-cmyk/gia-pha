package com.familya.treeaccess.adapter.in.grpc;

import io.grpc.stub.StreamObserver;

/**
 * Stand-in for the generated gRPC service base. In a CI environment,
 * the {@code contracts/grpc/treeaccess} Protobuf file is compiled by
 * the {@code protobuf-maven-plugin} and produces
 * {@code TreeAccessLookupGrpc.TreeAccessLookupImplBase}. The CI build
 * replaces this file with the generated source; the public method
 * signatures are identical.
 */
public final class TreeAccessProtoGrpc {
    private TreeAccessProtoGrpc() { }

    public static abstract class TreeAccessLookupImplBase
            implements io.grpc.BindableService {
        public void authorize(TreeAccessProto.AuthorizeRequest request,
                              StreamObserver<TreeAccessProto.AuthorizeResponse> responseObserver) {
            throw new UnsupportedOperationException("authorize() must be overridden");
        }
        public void getTreeRevision(TreeAccessProto.GetTreeRevisionRequest request,
                                    StreamObserver<TreeAccessProto.GetTreeRevisionResponse> responseObserver) {
            throw new UnsupportedOperationException("getTreeRevision() must be overridden");
        }

        @Override
        public io.grpc.ServerServiceDefinition bindService() {
            return io.grpc.ServerServiceDefinition.builder("familya.treeaccess.v1.TreeAccessLookup").build();
        }
    }
}