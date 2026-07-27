package com.familya.identity.adapter.in.grpc;

import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;

import java.util.UUID;

@GrpcService
public class IdentityLookupService extends IdentityLookupGrpc.IdentityLookupImplBase {

    @Override
    public void lookup(IdentityLookupRequest request, StreamObserver<IdentityLookupResponse> responseObserver) {
        IdentityLookupResponse.Builder b = IdentityLookupResponse.newBuilder();
        try {
            UUID id = UUID.fromString(request.getUserId());
            b.setUserId(id.toString()).setFound(true);
        } catch (IllegalArgumentException e) {
            b.setFound(false);
        }
        responseObserver.onNext(b.build());
        responseObserver.onCompleted();
    }
}
