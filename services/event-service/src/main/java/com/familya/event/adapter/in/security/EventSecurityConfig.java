package com.familya.event.adapter.in.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Cấu hình Spring Security cho event-service.
 *
 * <h2>Nguyên tắc</h2>
 * <ul>
 *   <li>Áp dụng riêng cho matcher {@code /api/v2/trees/**} và
 *       {@code /api/v2/internal/**} — các endpoint khác (health,...)
 *       do Spring Boot Actuator cung cấp.</li>
 *   <li>Stateless — không tạo session HTTP.</li>
 *   <li>CSRF tắt — phù hợp cho API JSON không dùng cookie.</li>
 *   <li>Mọi request đều được {@code permitAll()} — phân quyền thực hiện
 *       ở tầng application qua {@code authorization_projection}. Lý do:
 *       <ul>
 *           <li>Authentication đã được xử lý bởi API Gateway / sidecar
 *               và chuyển xuống qua {@code X-Acting-User}.</li>
 *           <li>Authorization cần projection domain (tươi/dirty) chứ
 *               không chỉ role ở biên.</li>
 *       </ul>
 *   </li>
 * </ul>
 *
 * @author gia-pha platform
 */
@Configuration
@EnableWebSecurity
public class EventSecurityConfig {

    /**
     * Định nghĩa {@link SecurityFilterChain} cho event-service.
     *
     * @param http builder của Spring Security.
     * @return filter chain đã cấu hình.
     * @throws Exception nếu cấu hình thất bại.
     */
    @Bean
    public SecurityFilterChain eventFilterChain(HttpSecurity http) throws Exception {
        http
            // Giới hạn matcher để không can thiệp endpoint khác (actuator).
            .securityMatcher("/api/v2/trees/**", "/api/v2/internal/**")
            // Tắt CSRF vì API JSON không dựa vào session/cookie.
            .csrf(csrf -> csrf.disable())
            // Stateless: không lưu session — phù hợp với kiến trúc microservice.
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // Phân quyền thực hiện ở tầng application (qua projection).
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
