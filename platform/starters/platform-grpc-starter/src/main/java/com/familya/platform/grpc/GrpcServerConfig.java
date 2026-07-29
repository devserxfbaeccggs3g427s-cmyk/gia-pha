package com.familya.platform.grpc;

import io.grpc.ServerBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * gRPC server defaults. Deadline propagation is enforced by the
 * underlying Netty server; client stubs that ignore deadlines will
 * trip a circuit breaker. Each service registers its own service
 * classes with the returned {@link ServerBuilder}.
 */
@Configuration
@ConditionalOnProperty(prefix = "familya.grpc.server", name = "enabled", havingValue = "true", matchIfMissing = false)
public class GrpcServerConfig {

    @Value("${familya.grpc.server.port:9090}")
    private int port;

    @Bean(destroyMethod = "shutdownNow")
    @Primary
    public io.grpc.Server grpcServer(io.grpc.BindableService... services) throws IOException {
        ServerBuilder<?> builder = ServerBuilder.forPort(port)
                .executor(Executors.newFixedThreadPool(8))
                .keepAliveTime(30, TimeUnit.SECONDS)
                .keepAliveTimeout(5, TimeUnit.SECONDS)
                .permitKeepAliveWithoutCalls(true)
                .maxConnectionIdle(60, TimeUnit.SECONDS);
        for (io.grpc.BindableService svc : services) {
            builder.addService(svc);
        }
        io.grpc.Server server = builder.build().start();
        Runtime.getRuntime().addShutdownHook(new Thread(server::shutdownNow, "grpc-shutdown"));
        return server;
    }
}
