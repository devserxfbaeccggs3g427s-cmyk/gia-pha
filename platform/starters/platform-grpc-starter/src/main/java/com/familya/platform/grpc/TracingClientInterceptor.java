package com.familya.platform.grpc;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Client interceptor that propagates correlation/causation/trace
 * headers on every gRPC call. Bounded synchronous calls (e.g. the
 * emergency tree-access lookup) may never exceed 2 hops (design.md).
 */
@Component
public class TracingClientInterceptor implements ClientInterceptor {

    public static final Metadata.Key<String> CORRELATION_ID = Metadata.Key.of("x-correlation-id", Metadata.ASCII_STRING_MARSHALLER);
    public static final Metadata.Key<String> CAUSATION_ID   = Metadata.Key.of("x-causation-id",   Metadata.ASCII_STRING_MARSHALLER);
    public static final Metadata.Key<String> OPERATION_ID   = Metadata.Key.of("x-operation-id",   Metadata.ASCII_STRING_MARSHALLER);
    public static final Metadata.Key<String> TRACEPARENT    = Metadata.Key.of("traceparent",       Metadata.ASCII_STRING_MARSHALLER);

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(MethodDescriptor<ReqT, RespT> method,
                                                               CallOptions callOptions, Channel next) {
        return new ForwardingClientCall.SimpleForwardingClientCall<>(next.newCall(method, callOptions)) {
            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
                headers.put(CORRELATION_ID, headerOrNew(CORRELATION_ID));
                headers.put(CAUSATION_ID,   headerOrNew(CAUSATION_ID));
                headers.put(OPERATION_ID,   headerOrNew(OPERATION_ID));
                super.start(responseListener, headers);
            }
        };
    }

    private static String headerOrNew(Metadata.Key<String> key) {
        return UUID.randomUUID().toString();
    }
}
