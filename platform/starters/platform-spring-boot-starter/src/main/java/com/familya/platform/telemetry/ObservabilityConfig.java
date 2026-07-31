package com.familya.platform.telemetry;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.ServerHttpObservationFilter;

/**
 * Cấu hình observability mặc định cho các dịch vụ.
 *
 * <p>Kết nối observation HTTP của Spring với Micrometer và OpenTelemetry bridge.
 * Các dịch vụ được khuyến nghị:</p>
 * <ul>
 *   <li>Phát ra metric tuỳ chỉnh thông qua bean {@link io.micrometer.core.instrument.MeterRegistry}.</li>
 *   <li>Phát ra span tuỳ chỉnh thông qua bean {@link ObservationRegistry}.</li>
 * </ul>
 *
 * <p><b>ServerHttpObservationFilter:</b> Filter này tự động tạo observation
 * cho mỗi HTTP request, giúp các metric (vd: http.server.requests) và trace
 * được ghi nhận mà không cần thêm code trong controller.</p>
 *
 * @author Family Tree Platform Team
 */
@Configuration
public class ObservabilityConfig {

    /**
     * Khai báo filter HTTP observation, kích hoạt việc tự động ghi metric
     * và span cho mọi request HTTP đến.
     *
     * @param registry {@link ObservationRegistry} do Spring/Micrometer cung cấp
     * @return {@link ServerHttpObservationFilter} sẵn sàng sử dụng
     */
    @Bean
    public ServerHttpObservationFilter serverHttpObservationFilter(ObservationRegistry registry) {
        // Tạo filter mới với registry đã cung cấp. Filter sẽ được tự động đăng ký
        // vào chuỗi filter của Spring và áp dụng cho mọi request HTTP.
        return new ServerHttpObservationFilter(registry);
    }
}
