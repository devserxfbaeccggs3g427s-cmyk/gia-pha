package com.familya.platform.telemetry;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.ServerHttpObservationFilter;

/**
 * Observability defaults. Wires Spring's HTTP observation into
 * Micrometer and the OpenTelemetry bridge. Services are expected to
 * emit custom metrics through the {@link io.micrometer.core.instrument.MeterRegistry}
 * bean and custom spans through the {@link ObservationRegistry}.
 */
@Configuration
public class ObservabilityConfig {

    @Bean
    public ServerHttpObservationFilter serverHttpObservationFilter(ObservationRegistry registry) {
        return new ServerHttpObservationFilter(registry);
    }
}
