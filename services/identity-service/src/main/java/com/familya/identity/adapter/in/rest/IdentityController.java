package com.familya.identity.adapter.in.rest;

import com.familya.identity.application.port.in.AuthenticateCommand;
import com.familya.identity.application.port.in.RegisterUserCommand;
import com.familya.identity.application.port.in.RevokeSessionCommand;
import com.familya.identity.application.port.in.VerifyEmailCommand;
import com.familya.identity.application.usecase.AuthenticateUseCase;
import com.familya.identity.application.usecase.RegisterUserUseCase;
import com.familya.identity.application.usecase.RevokeSessionUseCase;
import com.familya.identity.application.usecase.VerifyEmailUseCase;
import com.familya.identity.domain.model.Session;
import com.familya.platform.api.AsyncOperation;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST controller cung cấp API quản lý danh tính ở phiên bản
 * {@code /api/v2/identity}.
 *
 * <p>Controller là adapter đầu vào (driving adapter) trong kiến trúc
 * hexagonal, chịu trách nhiệm:
 * <ul>
 *     <li>Tiếp nhận và xác thực dữ liệu đầu vào (Bean Validation).</li>
 *     <li>Chuyển đổi request HTTP thành các {@code Command} của tầng
 *         application và ủy quyền xử lý cho use case tương ứng.</li>
 *     <li>Định dạng phản hồi (response), bao gồm cả cookie phiên và
 *         link đến tài nguyên liên quan.</li>
 * </ul>
 *
 * <p>Tất cả endpoint đều trả về {@code application/json} (do
 * {@link RequestMapping#produces()} cấu hình ở cấp class).
 *
 * @author Familya Platform Team
 * @since 1.0.0
 */
@RestController
@RequestMapping(path = "/api/v2/identity", produces = MediaType.APPLICATION_JSON_VALUE)
public class IdentityController {

    /** Use case đăng ký người dùng. */
    private final RegisterUserUseCase registerUser;
    /** Use case xác thực (đăng nhập). */
    private final AuthenticateUseCase authenticate;
    /** Use case xác minh email. */
    private final VerifyEmailUseCase verifyEmail;
    /** Use case thu hồi phiên đăng nhập. */
    private final RevokeSessionUseCase revokeSession;
    /**
     * Cờ cho biết cookie phiên có cần thuộc tính {@code Secure} hay không.
     * Cấu hình qua thuộc tính {@code familya.identity.session-cookie-secure}
     * (mặc định {@code true}).
     */
    private final boolean sessionCookieSecure;

    /**
     * Khởi tạo controller với các use case và cờ cấu hình.
     *
     * @param registerUser        use case đăng ký.
     * @param authenticate        use case xác thực.
     * @param verifyEmail         use case xác minh email.
     * @param revokeSession       use case thu hồi phiên.
     * @param sessionCookieSecure cờ bật {@code Secure} cho cookie phiên.
     */
    public IdentityController(RegisterUserUseCase registerUser,
                              AuthenticateUseCase authenticate,
                              VerifyEmailUseCase verifyEmail,
                              RevokeSessionUseCase revokeSession,
                              @Value("${familya.identity.session-cookie-secure:true}") boolean sessionCookieSecure) {
        this.registerUser = registerUser;
        this.authenticate = authenticate;
        this.verifyEmail = verifyEmail;
        this.revokeSession = revokeSession;
        this.sessionCookieSecure = sessionCookieSecure;
    }

    /**
     * Đăng ký người dùng mới.
     *
     * <p>Quy trình xử lý:
     * <ol>
     *     <li>Bean Validation tự động kiểm tra {@link RegisterUserRequest}
     *         nhờ annotation {@code @Valid}.</li>
     *     <li>Ủy quyền cho {@link RegisterUserUseCase#execute(RegisterUserCommand)}.</li>
     *     <li>Thiết lập header {@code Location} để client có thể truy
     *         vấn trạng thái người dùng vừa tạo.</li>
     *     <li>Trả về {@link HttpStatus#ACCEPTED} cùng với
     *         {@link AsyncOperation} cho phép client theo dõi quá trình
     *         xử lý bất đồng bộ (ví dụ: gửi email xác minh).</li>
     * </ol>
     *
     * @param idem header {@code Idempotency-Key} (tùy chọn) – dùng để
     *             hỗ trợ idempotency ở phía gateway.
     * @param req  payload chứa email, mật khẩu, IP và cờ yêu cầu xác minh.
     * @param res  đối tượng {@link HttpServletResponse} dùng để gắn header.
     * @return {@link ResponseEntity} chứa {@link AsyncOperation} với
     *         {@code 202 Accepted}.
     */
    @PostMapping("/users")
    public ResponseEntity<AsyncOperation> register(@RequestHeader(name = "Idempotency-Key", required = false) String idem,
                                                  @Valid @RequestBody RegisterUserRequest req,
                                                  HttpServletResponse res) {
        // Ủy quyền cho use case xử lý nghiệp vụ đăng ký.
        UUID userId = registerUser.execute(new RegisterUserCommand(req.email(), req.password(), req.ipAddress(), req.verificationRequired()));
        // Gắn header Location để client có thể định vị tài nguyên người dùng.
        res.setHeader("Location", "/api/v2/identity/users/" + userId);
        // Trả về 202 Accepted cùng thông tin operation bất đồng bộ để
        // client có thể polling trạng thái xử lý phía sau.
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(AsyncOperation.accepted(userId, "/api/v2/operations/" + userId));
    }

    /**
     * Bắt đầu một phiên đăng nhập (đăng nhập).
     *
     * <p>Quy trình xử lý:
     * <ol>
     *     <li>Xác thực payload qua {@code @Valid}.</li>
     *     <li>Ủy quyền cho {@link AuthenticateUseCase#execute(AuthenticateCommand)}.</li>
     *     <li>Tạo cookie {@code identity-session} với các thuộc tính
     *         bảo mật: {@code HttpOnly} (chặn truy cập từ JS),
     *         {@code SameSite=Lax} (chống CSRF), {@code Secure} (chỉ
     *         gửi qua HTTPS nếu được bật), {@code Path=/} (áp dụng cho
     *         toàn bộ ứng dụng) và {@code Max-Age} được tính từ
     *         {@code absoluteExpiresAt - createdAt} (tính bằng giây).</li>
     *     <li>Trả về thông tin phiên dưới dạng {@link SessionResponse}.</li>
     * </ol>
     *
     * @param idem header {@code Idempotency-Key} (tùy chọn).
     * @param req  payload chứa email, mật khẩu, IP và user-agent.
     * @param res  đối tượng phản hồi HTTP dùng để gắn cookie.
     * @return {@link ResponseEntity} chứa thông tin phiên (200 OK).
     */
    @PostMapping("/sessions")
    public ResponseEntity<SessionResponse> startSession(@RequestHeader(name = "Idempotency-Key", required = false) String idem,
                                                        @Valid @RequestBody AuthenticateRequest req,
                                                        HttpServletResponse res) {
        // Ủy quyền cho use case đăng nhập.
        Session session = authenticate.execute(new AuthenticateCommand(req.email(), req.password(), req.ipAddress(), req.userAgent()));
        // Tính Max-Age (giây) dựa trên khoảng cách giữa thời điểm hết hạn tuyệt đối
        // và thời điểm tạo phiên – đảm bảo cookie hết hạn đồng bộ với session.
        String cookie = String.format("identity-session=%s; HttpOnly; %sSameSite=Lax; Path=/; Max-Age=%d",
                session.id(), sessionCookieSecure ? "Secure; " : "", session.absoluteExpiresAt().getEpochSecond() - session.createdAt().getEpochSecond());
        // Gắn cookie vào header Set-Cookie.
        res.setHeader("Set-Cookie", cookie);
        // Trả về thông tin phiên để client có thể lưu trữ và sử dụng.
        return ResponseEntity.ok(new SessionResponse(session.id().toString(), session.userId().toString(), session.absoluteExpiresAt()));
    }

    /**
     * Thu hồi phiên hiện tại của người dùng đang đăng nhập.
     *
     * <p>Quy trình xử lý:
     * <ol>
     *     <li>Đọc hai header: {@code X-Acting-User} (UUID của người
     *         dùng đang thực hiện yêu cầu) và {@code X-Session-Id}
     *         (UUID của phiên cần thu hồi).</li>
     *     <li>Ủy quyền cho {@link RevokeSessionUseCase#execute(RevokeSessionCommand)},
     *         use case sẽ kiểm tra quyền sở hữu phiên và đánh dấu
     *         thu hồi trong cơ sở dữ liệu.</li>
     *     <li>Trả về {@code 204 No Content} theo chuẩn REST.</li>
     * </ol>
     *
     * @param actingUser UUID của người dùng đang đăng nhập (lấy từ header).
     * @param sessionId  UUID của phiên cần thu hồi (lấy từ header).
     * @return {@link ResponseEntity} rỗng với mã 204.
     */
    @PostMapping("/sessions/current/revoke")
    public ResponseEntity<Void> revokeCurrent(@RequestHeader("X-Acting-User") UUID actingUser,
                                              @RequestHeader("X-Session-Id") UUID sessionId) {
        // Ủy quyền cho use case thu hồi phiên.
        revokeSession.execute(new RevokeSessionCommand(sessionId, actingUser));
        // 204 No Content báo hiệu thao tác đã hoàn tất, không có payload trả về.
        return ResponseEntity.noContent().build();
    }

    /**
     * Xác minh địa chỉ email người dùng.
     *
     * <p>Quy trình xử lý:
     * <ol>
     *     <li>Lấy {@code userId} từ path variable.</li>
     *     <li>Ủy quyền cho {@link VerifyEmailUseCase#execute(VerifyEmailCommand)}.</li>
     *     <li>Trả về {@code 202 Accepted} cùng {@link AsyncOperation}
     *         cho phép client theo dõi quá trình xử lý.</li>
     * </ol>
     *
     * @param userId UUID người dùng cần xác minh (lấy từ path).
     * @param req    payload chứa token xác minh.
     * @return {@link ResponseEntity} chứa {@link AsyncOperation} với 202.
     */
    @PostMapping("/users/{userId}/verify")
    public ResponseEntity<AsyncOperation> verify(@PathVariable UUID userId, @RequestBody VerifyRequest req) {
        // Ủy quyền cho use case xác minh email.
        UUID id = verifyEmail.execute(new VerifyEmailCommand(userId, req.token()));
        // Trả về 202 Accepted cùng operation id để client theo dõi.
        return ResponseEntity.accepted().body(AsyncOperation.accepted(id, "/api/v2/operations/" + id));
    }

    /**
     * Payload cho endpoint {@code POST /api/v2/identity/users} (đăng ký).
     *
     * @param email                địa chỉ email hợp lệ và không rỗng.
     * @param password             mật khẩu từ 12 đến 256 ký tự.
     * @param ipAddress            địa chỉ IP của client (phục vụ rate limit).
     * @param verificationRequired cờ cho biết người dùng có cần xác minh email hay không.
     */
    public record RegisterUserRequest(@Email @NotBlank String email,
                                      @NotBlank @Size(min = 12, max = 256) String password,
                                      String ipAddress,
                                      boolean verificationRequired) { }

    /**
     * Payload cho endpoint {@code POST /api/v2/identity/sessions} (đăng nhập).
     *
     * @param email     địa chỉ email hợp lệ.
     * @param password  mật khẩu (cùng ràng buộc với lúc đăng ký).
     * @param ipAddress địa chỉ IP client (phục vụ rate limit / log).
     * @param userAgent chuỗi User-Agent (phục vụ audit và bảo mật).
     */
    public record AuthenticateRequest(@Email @NotBlank String email,
                                      @NotBlank @Size(min = 12, max = 256) String password,
                                      String ipAddress,
                                      String userAgent) { }

    /**
     * Payload cho endpoint {@code POST /api/v2/identity/users/{userId}/verify}.
     *
     * @param token mã xác minh email được gửi qua email.
     */
    public record VerifyRequest(@NotBlank String token) { }

    /**
     * Phản hồi cho endpoint đăng nhập, dùng để client dễ dàng lưu trữ
     * và sử dụng thông tin phiên.
     *
     * @param sessionId         UUID phiên.
     * @param userId            UUID người dùng sở hữu phiên.
     * @param absoluteExpiresAt thời điểm hết hạn tuyệt đối của phiên.
     */
    public record SessionResponse(String sessionId, String userId, java.time.Instant absoluteExpiresAt) { }
}
