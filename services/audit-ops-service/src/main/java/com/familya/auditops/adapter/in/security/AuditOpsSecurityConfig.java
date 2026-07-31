/**
 * Cấu hình bảo mật cho dịch vụ audit-ops.
 *
 * <p>Cấu hình bảo mật mặc định của platform ({@code SecurityDefaultsConfig})
 * vẫn được áp dụng cho {@code /actuator/**} và các route bắt còn lại.
 * Filter chain trong lớp này chỉ áp dụng cho các route của audit-ops:</p>
 *
 * <ul>
 *   <li>{@code /api/v2/audit-ops/**}: yêu cầu vai trò
 *       {@code AUDIT_OPERATOR} hoặc {@code PLATFORM}. Role đến từ
 *       bridge token của platform ({@code platform-security-starter})
 *       hoặc từ tài khoản dịch vụ platform cho backplane calls.</li>
 *   <li>{@code /api/v2/operations/**} và {@code /api/v2/audit-ops/operations/**}:
 *       mở trong cluster; Gateway xác thực danh tính người gọi trước khi
 *       chuyển tiếp. Phân quyền nghiệp vụ do bounded context sở hữu thực thi
 *       khi cuộc gọi xuất phát từ dịch vụ khác.</li>
 * </ul>
 */
package com.familya.auditops.adapter.in.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Lớp {@link Configuration} khai báo các {@link SecurityFilterChain}
 * cho audit-ops.
 *
 * <p>Đặc điểm:</p>
 * <ul>
 *   <li>Tắt CSRF vì dịch vụ là REST stateless.</li>
 *   <li>Chính sách phiên STATELESS: không tạo session HTTP.</li>
 *   <li>Sử dụng {@code securityMatcher} để áp dụng chain đúng cho từng
 *       nhóm URL mà không xung đột với chain mặc định của platform.</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
public class AuditOpsSecurityConfig {

    /**
     * Filter chain cho {@code /api/v2/audit-ops/**}.
     *
     * <p>Các bước cấu hình:</p>
     * <ol>
     *   <li>Chỉ áp dụng cho các URL khớp với matcher.</li>
     *   <li>Tắt CSRF.</li>
     *   <li>Đặt chính sách phiên là STATELESS.</li>
     *   <li>Endpoint audit cho phép {@code AUDIT_OPERATOR} hoặc {@code PLATFORM}.</li>
     *   <li>Mọi URL còn lại trong chain này cũng yêu cầu 1 trong 2 role trên.</li>
     *   <li>Mọi URL khác (nếu lọt vào) sẽ bị từ chối.</li>
     * </ol>
     *
     * @param http đối tượng {@link HttpSecurity} được inject
     * @return {@link SecurityFilterChain} đã cấu hình
     * @throws Exception nếu có lỗi khi cấu hình
     */
    @Bean
    public SecurityFilterChain auditOpsFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/api/v2/audit-ops/**")
            .csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v2/audit-ops/operations/*/audit").hasAnyRole("AUDIT_OPERATOR", "PLATFORM")
                .requestMatchers("/api/v2/audit-ops/**").hasAnyRole("AUDIT_OPERATOR", "PLATFORM")
                .anyRequest().denyAll());
        return http.build();
    }

    /**
     * Filter chain cho {@code /api/v2/operations/**} và
     * {@code /api/v2/audit-ops/operations/**}.
     *
     * <p>Các URL này được mở hoàn toàn trong cluster; Gateway hoặc
     * bounded context sở hữu sẽ chịu trách nhiệm xác thực và phân quyền.</p>
     *
     * @param http đối tượng {@link HttpSecurity}
     * @return {@link SecurityFilterChain} đã cấu hình
     * @throws Exception nếu có lỗi khi cấu hình
     */
    @Bean
    public SecurityFilterChain operationLookupFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/api/v2/operations/**", "/api/v2/audit-ops/operations/**")
            .csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}