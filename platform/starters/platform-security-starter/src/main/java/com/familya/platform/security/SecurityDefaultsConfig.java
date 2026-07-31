package com.familya.platform.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Cấu hình bảo mật mặc định áp dụng cho mọi dịch vụ Spring Boot trong nền tảng.
 *
 * <p>Các dịch vụ có nhu cầu riêng (ví dụ: {@code identity-service} với cầu nối
 * NextAuth) có thể override hoàn toàn {@link SecurityFilterChain} của lớp này
 * bằng cách khai báo bean mới cùng kiểu.</p>
 *
 * <p><b>Các chính sách mặc định:</b></p>
 * <ul>
 *   <li>Quản lý phiên ở chế độ {@code STATELESS} — phù hợp cho các API cross-service
 *       vốn dựa vào JWT/Bridge token thay vì session truyền thống.</li>
 *   <li>CSRF bị tắt cho API stateless; bật lại cho bất kỳ luồng form-based
 *       nào của identity flow.</li>
 *   <li>Endpoint {@code /actuator/health/**} và {@code /actuator/info} được
 *       phép truy cập công khai để phục vụ liveness probe.</li>
 *   <li>Các endpoint actuator còn lại yêu cầu role {@code PLATFORM} (tài khoản
 *       dịch vụ nội bộ của nền tảng).</li>
 *   <li>Mọi endpoint khác đều yêu cầu xác thực.</li>
 * </ul>
 *
 * <p>Basic authentication được bật mặc định để phục vụ các tác vụ vận hành
 * (chạy script tự động, kiểm tra sức khỏe), nhưng trong môi trường production
 * nên tắt hoặc giới hạn qua cấu hình.</p>
 *
 * @author Family Tree Platform Team
 */
@Configuration
@EnableWebSecurity
public class SecurityDefaultsConfig {

    /**
     * Khai báo chuỗi filter bảo mật mặc định cho toàn bộ ứng dụng.
     *
     * @param http đối tượng {@link HttpSecurity} do Spring cung cấp để cấu hình bảo mật
     * @return {@link SecurityFilterChain} đã được cấu hình
     * @throws Exception nếu có lỗi trong quá trình cấu hình Spring Security
     */
    @Bean
    public SecurityFilterChain platformFilterChain(HttpSecurity http) throws Exception {
        // Bước 1: Cấu hình chính sách phiên ở dạng STATELESS, đảm bảo server
        // không tạo session cho mỗi request, phù hợp với kiến trúc API thuần JWT.
        http
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // Bước 2: Tắt CSRF cho API stateless. Khi cần tích hợp form-based
            // identity flow, endpoint đó phải override chuỗi filter này và bật lại CSRF.
            .csrf(csrf -> csrf.disable())

            // Bước 3: Cấu hình các quy tắc phân quyền theo thứ tự ưu tiên từ
            // cụ thể đến tổng quát. Spring Security sẽ khớp rule đầu tiên thỏa mãn.
            .authorizeHttpRequests(auth -> auth
                // Các endpoint health/info được mở công khai cho Kubernetes probe
                // và các công cụ giám sát.
                .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()

                // Các endpoint actuator khác yêu cầu role PLATFORM (service account).
                .requestMatchers("/actuator/**").hasRole("PLATFORM")

                // Mọi request còn lại đều yêu cầu xác thực.
                .anyRequest().authenticated())

            // Bước 4: Bật HTTP Basic với cấu hình mặc định. Trong production,
            // nên thay bằng JWT filter hoặc Bridge token filter.
            .httpBasic(Customizer.withDefaults());

        // Bước 5: Build và trả về SecurityFilterChain đã cấu hình.
        return http.build();
    }
}
