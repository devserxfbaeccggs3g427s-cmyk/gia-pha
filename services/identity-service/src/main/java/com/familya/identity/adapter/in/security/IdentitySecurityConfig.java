package com.familya.identity.adapter.in.security;

import com.familya.identity.application.port.out.IdentityRepository;
import com.familya.platform.security.BridgeTokenIssuer;
import com.nimbusds.jwt.SignedJWT;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Cấu hình bảo mật cho {@code identity-service}.
 *
 * <p>Lớp này thiết lập {@link SecurityFilterChain} chỉ áp dụng cho
 * các đường dẫn {@code /api/v2/identity/**}, đồng thời đăng ký
 * {@link SessionAuthenticationFilter} – một filter tùy chỉnh chịu
 * trách nhiệm xác thực dựa trên "Bridge Token" do các microservice
 * khác (đặc biệt là {@code web-client}) cấp phát.
 *
 * <p>Các nguyên tắc bảo mật được áp dụng:
 * <ul>
 *     <li>Tắt CSRF vì service hoạt động ở chế độ stateless, không
 *         dùng session form.</li>
 *     <li>Chính sách phiên: {@code STATELESS} – mỗi request phải
 *         tự mang theo thông tin xác thực.</li>
 *     <li>Một số endpoint công khai (đăng ký, đăng nhập, xác minh)
 *         được phép truy cập không cần xác thực thông qua
 *         {@code permitAll()}; các endpoint còn lại yêu cầu
 *         xác thực thành công.</li>
 * </ul>
 *
 * @author Familya Platform Team
 * @since 1.0.0
 */
@Configuration
@EnableWebSecurity
public class IdentitySecurityConfig {

    /**
     * Khai báo chuỗi filter bảo mật dành riêng cho {@code identity-service}.
     *
     * <p>Quy trình cấu hình:
     * <ol>
     *     <li>Thu hẹp phạm vi áp dụng bằng
     *         {@code securityMatcher("/api/v2/identity/**")} – tránh
     *         ảnh hưởng đến các đường dẫn khác (như actuator, health).</li>
     *     <li>Tắt CSRF vì service không sử dụng session form.</li>
     *     <li>Thiết lập chính sách phiên là {@code STATELESS}.</li>
     *     <li>Cho phép truy cập không xác thực với các endpoint công
     *         khai: đăng ký, đăng nhập và xác minh email.</li>
     *     <li>Chèn {@link SessionAuthenticationFilter} trước
     *         {@code UsernamePasswordAuthenticationFilter} để xử lý
     *         bridge token đầu tiên.</li>
     * </ol>
     *
     * @param http   đối tượng {@link HttpSecurity} do Spring cung cấp.
     * @param filter filter tùy chỉnh để xác thực bridge token.
     * @return {@link SecurityFilterChain} đã cấu hình.
     * @throws Exception nếu cấu hình gặp lỗi (ví dụ: builder không hợp lệ).
     */
    @Bean
    public SecurityFilterChain identityFilterChain(HttpSecurity http,
                                                   SessionAuthenticationFilter filter) throws Exception {
        http
            // Bước 1: giới hạn filter chain chỉ áp dụng cho API identity.
            .securityMatcher("/api/v2/identity/**")
            // Bước 2: tắt CSRF – service hoạt động stateless.
            .csrf(csrf -> csrf.disable())
            // Bước 3: cấu hình chính sách phiên là STATELESS.
            .sessionManagement(s -> s.sessionCreationPolicy(org.springframework.security.config.http.SessionCreationPolicy.STATELESS))
            // Bước 4: cho phép truy cập không xác thực với endpoint công khai
            // và yêu cầu xác thực với các endpoint còn lại.
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v2/identity/users", "/api/v2/identity/sessions", "/api/v2/identity/users/*/verify").permitAll()
                .anyRequest().authenticated())
            // Bước 5: chèn SessionAuthenticationFilter trước UsernamePasswordAuthenticationFilter
            // để có cơ hội thiết lập authentication cho request trước.
            .addFilterBefore(filter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}

/**
 * Filter xác thực dựa trên "Bridge Token" (JWT nội bộ) cho các request
 * tới {@code /api/v2/identity/**}.
 *
 * <p>Bridge Token được cấp bởi {@link BridgeTokenIssuer} và được gắn
 * trong header {@code X-NextAuth-Bridge-Token}. Khi token hợp lệ,
 * subject (UUID người dùng) sẽ được lưu vào request attribute
 * {@code familya.principal} để các tầng phía sau có thể sử dụng.
 *
 * <p>Filter này cố ý được tách riêng khỏi cấu hình filter chain
 * chính để có thể quản lý thứ tự ưu tiên ({@link Order}) và tái sử dụng
 * ở nhiều nơi nếu cần.
 *
 * @author Familya Platform Team
 * @since 1.0.0
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
class SessionAuthenticationFilter extends OncePerRequestFilter {

    /** Logger ghi lại thông tin xác thực. */
    private static final Logger LOG = LoggerFactory.getLogger(SessionAuthenticationFilter.class);

    /** Cổng ra truy cập dữ liệu identity – hiện dùng cho các tác vụ mở rộng trong tương lai. */
    private final IdentityRepository repo;
    /** Bộ cấp phát / xác minh Bridge Token. */
    private final BridgeTokenIssuer bridge;
    /** Cờ cho biết bridge token có bắt buộc hay không. */
    private final boolean bridgeRequired;

    /**
     * Khởi tạo filter với các phụ thuộc.
     *
     * @param repo            Cổng dữ liệu identity.
     * @param bridgeRequired  cờ bắt buộc bridge token (cấu hình qua
     *                        {@code familya.identity.bridge.required}).
     * @throws Exception nếu khởi tạo {@link BridgeTokenIssuer} gặp lỗi.
     */
    public SessionAuthenticationFilter(IdentityRepository repo,
                                       @Value("${familya.identity.bridge.required:true}") boolean bridgeRequired) throws Exception {
        this.repo = repo;
        // Khởi tạo BridgeTokenIssuer với issuer "familya" và TTL 300 giây.
        this.bridge = new BridgeTokenIssuer("familya", 300L);
        this.bridgeRequired = bridgeRequired;
    }

    /**
     * Thực hiện xác thực trên mỗi request đi qua filter.
     *
     * <p>Quy trình xử lý:
     * <ol>
     *     <li>Nếu request không yêu cầu xác thực (ví dụ: đăng ký,
     *         đăng nhập, xác minh) thì cho phép đi tiếp.</li>
     *     <li>Đọc header {@code X-NextAuth-Bridge-Token}. Nếu thiếu
     *         hoặc rỗng -> trả về 401.</li>
     *     <li>Gọi {@link BridgeTokenIssuer#verify(String)} để xác
     *         minh chữ ký và thời hạn. Nếu không hợp lệ -> 401.</li>
     *     <li>Phân tích JWT, lấy subject làm {@code userId} và lưu
     *         vào request attribute để các tầng sau sử dụng. Nếu có
     *         lỗi phân tích -> 401.</li>
     *     <li>Cho request đi tiếp qua các filter tiếp theo.</li>
     * </ol>
     *
     * @param request  request HTTP hiện tại.
     * @param response response HTTP hiện tại.
     * @param chain    chuỗi filter để chuyển tiếp request.
     * @throws ServletException nếu filter downstream ném lỗi.
     * @throws IOException      nếu xảy ra lỗi I/O khi ghi response.
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        // Bước 1: bỏ qua các endpoint công khai để tránh yêu cầu token không cần thiết.
        if (!requiresAuth(request)) {
            chain.doFilter(request, response);
            return;
        }
        // Bước 2: đọc header bridge token.
        String token = request.getHeader("X-NextAuth-Bridge-Token");
        if (token == null || token.isBlank()) {
            unauthorized(response, "bridge token missing");
            return;
        }
        try {
            // Bước 3: xác minh chữ ký / thời hạn token.
            if (!bridge.verify(token)) {
                unauthorized(response, "bridge token invalid");
                return;
            }
            // Bước 4: phân tích JWT và lấy userId (subject).
            SignedJWT jwt = SignedJWT.parse(token);
            UUID userId = UUID.fromString(jwt.getJWTClaimsSet().getSubject());
            // Lưu vào request attribute để controller có thể đọc khi cần.
            request.setAttribute("familya.principal", userId);
        } catch (Exception e) {
            // Bất kỳ lỗi nào trong quá trình phân tích đều dẫn đến 401.
            LOG.debug("Bridge token validation failed", e);
            unauthorized(response, "bridge token malformed");
            return;
        }
        // Bước 5: cho request đi tiếp qua các filter tiếp theo.
        chain.doFilter(request, response);
    }

    /**
     * Xác định xem request hiện tại có cần xác thực hay không.
     *
     * <p>Các đường dẫn công khai (đăng ký, đăng nhập, xác minh email)
     * sẽ luôn đi qua mà không cần bridge token. Logic này đảm bảo
     * filter không gửi phản hồi 401 cho các endpoint đăng ký/đăng nhập.
     *
     * @param req request HTTP hiện tại.
     * @return {@code true} nếu request yêu cầu xác thực, {@code false}
     *         nếu là endpoint công khai.
     */
    private boolean requiresAuth(HttpServletRequest req) {
        String p = req.getRequestURI();
        // Endpoint công khai: POST /users, POST /sessions, POST /users/{id}/verify.
        return !(p.endsWith("/users") || p.endsWith("/sessions") || p.matches(".*/users/[^/]+/verify"));
    }

    /**
     * Ghi phản hồi 401 Unauthorized với payload JSON mô tả lý do.
     *
     * @param res    response HTTP cần ghi.
     * @param reason mô tả ngắn lý do từ chối truy cập (sẽ được đưa vào JSON).
     * @throws IOException nếu ghi response thất bại.
     */
    private void unauthorized(HttpServletResponse res, String reason) throws IOException {
        res.setStatus(401);
        res.setContentType("application/json");
        // Sử dụng định dạng JSON đơn giản để client dễ parse và hiển thị lỗi.
        res.getWriter().write("{\"code\":\"unauthorized\",\"message\":\"" + reason + "\"}");
    }
}
