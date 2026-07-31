package com.familya.relationship.adapter.in.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Cấu hình Spring Security cho Relationship service.
 * <p>
 * Cấu hình này áp dụng cho các đường dẫn:
 * </p>
 * <ul>
 *   <li>{@code /api/v2/trees/**} - REST API chính cho client.</li>
 *   <li>{@code /api/v2/internal/**} - các API nội bộ (migration, debug, ...).</li>
 * </ul>
 *
 * <h2>Chính sách</h2>
 * <ul>
 *   <li><b>Stateless</b>: không tạo session HTTP - mọi request phải mang đầy đủ
 *       thông tin xác thực (JWT, header nội bộ).</li>
 *   <li><b>CSRF tắt</b>: các API là JSON, không dùng cookie nên CSRF không cần.</li>
 *   <li><b>Permit all</b>: việc phân quyền chi tiết được thực hiện ở tầng use
 *       case dựa trên projection phân quyền, không phải ở tầng HTTP filter.</li>
 * </ul>
 *
 * <p>
 * Lưu ý: cấu hình này chỉ áp dụng cho các matcher đã chỉ định; các đường dẫn
 * khác (ví dụ: endpoint Kafka health, actuator) sẽ được cấu hình bởi module
 * khác của platform.
 * </p>
 */
@Configuration
@EnableWebSecurity
public class RelationshipSecurityConfig {

    /**
     * Khai báo chuỗi filter cho phạm vi REST của Relationship service.
     *
     * @param http đối tượng {@link HttpSecurity} do Spring cung cấp
     * @return {@link SecurityFilterChain} đã cấu hình
     * @throws Exception nếu có lỗi khi cấu hình
     */
    @Bean
    public SecurityFilterChain relationshipFilterChain(HttpSecurity http) throws Exception {
        http
            // Chỉ áp dụng chain này cho các URL của Relationship service.
            .securityMatcher("/api/v2/trees/**", "/api/v2/internal/**")
            // Tắt CSRF vì API thuần JSON và không dùng session/cookie.
            .csrf(csrf -> csrf.disable())
            // Stateless: không tạo session HTTP.
            .sessionManagement(s -> s.sessionCreationPolicy(
                    org.springframework.security.config.http.SessionCreationPolicy.STATELESS))
            // Permit all ở tầng filter - phân quyền chi tiết được xử lý ở tầng use case.
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}