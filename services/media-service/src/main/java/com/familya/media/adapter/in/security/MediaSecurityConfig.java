package com.familya.media.adapter.in.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Cấu hình Spring Security cho media-service.
 * <p>
 * Filter chain này chỉ áp dụng cho các path bắt đầu bằng {@code /api/v2/trees/**},
 * {@code /api/v2/internal/**}, {@code /api/v2/public/**} và {@code /api/v2/media/**}.
 * Vô hiệu hóa CSRF (vì dịch vụ stateless, xác thực qua gateway), dùng
 * {@code STATELESS} session và mặc định permitAll — quyết định phân quyền cuối
 * cùng thuộc về gateway và các use case thông qua {@code MediaAuthorization}.
 */
@Configuration
@EnableWebSecurity
public class MediaSecurityConfig {

    /**
     * Khai báo filter chain cho media-service.
     *
     * @param http cấu hình HttpSecurity.
     * @return {@link SecurityFilterChain} đã build.
     * @throws Exception nếu cấu hình không hợp lệ.
     */
    @Bean
    public SecurityFilterChain mediaFilterChain(HttpSecurity http) throws Exception {
        http
            // Chỉ áp dụng cho 4 nhóm path của media-service.
            .securityMatcher("/api/v2/trees/**", "/api/v2/internal/**", "/api/v2/public/**", "/api/v2/media/**")
            // Vô hiệu hóa CSRF: dịch vụ là stateless và xác thực qua gateway.
            .csrf(csrf -> csrf.disable())
            // Stateless: không tạo HTTP session, JWT/header được verify ở gateway.
            .sessionManagement(s -> s.sessionCreationPolicy(
                    org.springframework.security.config.http.SessionCreationPolicy.STATELESS))
            // PermitAll ở mức filter; quyết định authz thực sự nằm trong use case + MediaAuthorization.
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
