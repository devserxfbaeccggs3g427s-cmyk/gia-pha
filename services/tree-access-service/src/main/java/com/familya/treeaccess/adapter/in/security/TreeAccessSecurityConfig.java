package com.familya.treeaccess.adapter.in.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Cấu hình Spring Security cho REST controller của tree-access-service.
 *
 * <p>Quyết định:</p>
 * <ul>
 *   <li>Chỉ áp dụng cho các đường dẫn {@code /api/v2/trees/**}.</li>
 *   <li>Tắt CSRF vì hệ thống sử dụng JWT/header-based auth từ gateway.</li>
 *   <li>Stateless session — phù hợp với kiến trúc microservice.</li>
 *   <li>Cho phép truy cập mặc định vì cổng API phía trước chịu trách nhiệm xác thực.</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
public class TreeAccessSecurityConfig {

    /**
     * Cấu hình chuỗi filter bảo mật cho các endpoint cây.
     *
     * @param http cấu hình HTTP security của Spring Security
     * @return chuỗi filter đã cấu hình
     * @throws Exception nếu có lỗi khi xây dựng filter chain
     */
    @Bean
    public SecurityFilterChain treeAccessFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/api/v2/trees/**")
            .csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(
                    org.springframework.security.config.http.SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}