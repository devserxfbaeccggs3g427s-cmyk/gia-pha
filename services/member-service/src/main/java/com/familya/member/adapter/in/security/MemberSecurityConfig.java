package com.familya.member.adapter.in.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Cấu hình bảo mật HTTP cho Member Service. Vô hiệu hóa CSRF và sử dụng chính sách
 * STATELESS (vì dịch vụ chỉ phục vụ API). Ủy quyền được thực hiện ở tầng use case thông
 * qua {@link com.familya.platform.projection.AuthorizationProjection}, vì vậy filter chain
 * chỉ cần {@code permitAll} cho các đường dẫn liên quan.
 *
 * <p>Bean {@code @Configuration} thuộc tầng adapter-in/security.
 */
@Configuration
@EnableWebSecurity
public class MemberSecurityConfig {

    /**
     * Khai báo filter chain cho các đường dẫn {@code /api/v2/trees/**} và {@code /api/v2/internal/**}.
     *
     * @param http cấu hình {@link HttpSecurity} do Spring Security cung cấp
     * @return filter chain đã cấu hình
     * @throws Exception nếu có lỗi trong quá trình cấu hình
     */
    @Bean
    public SecurityFilterChain memberFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/api/v2/trees/**", "/api/v2/internal/**")
            .csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(
                    org.springframework.security.config.http.SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}