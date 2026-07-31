package com.familya.sharing.adapter.in.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Cấu hình bảo mật HTTP cho sharing-service.
 * <p>
 * Filter chain này chỉ áp dụng cho các đường dẫn thuộc sharing-service:
 * <ul>
 *     <li>{@code /api/v2/sharing/**} &mdash; các endpoint REST chính.</li>
 *     <li>{@code /api/v2/internal/**} &mdash; các endpoint migration/internal.</li>
 *     <li>{@code /api/v2/public/**} &mdash; các endpoint công khai.</li>
 * </ul>
 * Các thiết lập:
 * <ul>
 *     <li>CSRF bị tắt &mdash; phù hợp với API JSON được bảo vệ bởi các cơ chế
 *         khác (token, mạng nội bộ, gateway).</li>
 *     <li>Session ở chế độ STATELESS &mdash; mỗi yêu cầu phải mang đầy đủ thông
 *         tin xác thực/ủy quyền.</li>
 *     <li>Mọi yêu cầu được cho phép ({@code permitAll}) ở mức Spring Security
 *         &mdash; việc phân quyền chi tiết sẽ được xử lý bởi các use case và
 *         header {@code X-Acting-User} (qua gateway/identity-service).</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
public class SharingSecurityConfig {

    /**
     * Khai báo filter chain áp dụng cho các URL của sharing-service.
     *
     * @param http cấu hình {@link HttpSecurity} do Spring cung cấp.
     * @return {@link SecurityFilterChain} đã cấu hình.
     * @throws Exception nếu có lỗi khi cấu hình Spring Security.
     */
    @Bean
    public SecurityFilterChain sharingFilterChain(HttpSecurity http) throws Exception {
        http
            // Giới hạn chain này chỉ áp dụng cho các URL của sharing-service.
            .securityMatcher("/api/v2/sharing/**", "/api/v2/internal/**", "/api/v2/public/**")
            // Tắt CSRF vì đây là API JSON, không dùng session.
            .csrf(csrf -> csrf.disable())
            // Stateless &mdash; mỗi request tự chứa thông tin xác thực.
            .sessionManagement(s -> s.sessionCreationPolicy(
                    org.springframework.security.config.http.SessionCreationPolicy.STATELESS))
            // Cho phép tất cả request ở mức HTTP filter; phân quyền chi tiết do use case xử lý.
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}