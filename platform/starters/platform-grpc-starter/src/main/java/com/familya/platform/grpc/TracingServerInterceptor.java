package com.familya.platform.grpc;

import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;
import org.springframework.context.annotation.Configuration;

import java.util.UUID;

/**
 * Server interceptor that extracts correlation/causation/operation
 * headers and places them into a {@link io.grpc.Context} so
 * downstream handlers can use them for logging and tracing.
 */
@Configuration
@GrpcGlobalServerInterceptor
public class TracingServerInterceptor implements ServerInterceptor {

    public static final Context.Key<String> CORRELATION_ID = Context.key("x-correlation-id");
    public static final Context.Key<String> CAUSATION_ID   = Context.key("x-causation-id");
    public static final Context.Key<String> OPERATION_ID   = Context.key("x-operation-id");

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call,
                                                                 Metadata headers,
                                                                 ServerCallHandler<ReqT, RespT> next) {
        Context ctx = Context.current()
                .withValue(CORRELATION_ID, firstOrRandom(headers, "x-correlation-id"))
                .withValue(CAUSATION_ID,   firstOrRandom(headers, "x-causation-id"))
                .withValue(OPERATION_ID,   firstOrRandom(headers, "x-operation-id"));
        return Contexts.interceptCall(ctx, call, headers, next);
    }

    private static String firstOrRandom(Metadata headers, String name) {
        Metadata.Key<String> key = Metadata.Key.of(name, Metadata.ASCII_STRING_MARSHALLER);
        String v = headers.get(key);
        return v != null ? v : UUID.randomUUID().toString();
    }
}
