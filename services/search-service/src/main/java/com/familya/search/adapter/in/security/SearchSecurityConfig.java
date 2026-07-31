package com.familya.search.adapter.in.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Cấu hình bảo mật cho REST API của search service.
 *
 * <p>Vì search service được gọi từ API gateway đã xác thực, các endpoint ở
 * đây mặc định cho phép truy cập - quyền được kiểm tra lại ở tầng use case
 * thông qua {@code SearchAuthorization}.</p>
 *
 * <p>Cấu hình này:</p>
 * <ul>
 *   <li>Áp dụng cho {@code /api/v2/search/**} và {@code /api/v2/internal/**}.</li>
 *   <li>Tắt CSRF (REST API stateless).</li>
 *   <li>Bật chế độ phiên STATELESS (mỗi request tự xác thực).</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
public class SearchSecurityConfig {

    /**
     * Cấu hình filter chain bảo mật cho các endpoint của service.
     *
     * @param http cấu hình HTTP security của Spring.
     * @return {@link SecurityFilterChain} đã được cấu hình.
     * @throws Exception nếu có lỗi khi cấu hình.
     */
    @Bean
    public SecurityFilterChain searchFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/api/v2/search/**", "/api/v2/internal/**")
            .csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(
                    org.springframework.security.config.http.SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
