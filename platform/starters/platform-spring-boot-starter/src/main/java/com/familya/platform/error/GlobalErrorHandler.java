package com.familya.platform.error;

import com.familya.platform.api.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Bộ xử lý lỗi toàn cục cho các dịch vụ Spring Boot trong nền tảng.
 *
 * <p>Lớp này sử dụng {@link RestControllerAdvice} để bắt mọi exception được
 * ném ra từ controller và ánh xạ chúng sang envelope {@link ErrorResponse} ổn định.</p>
 *
 * <p><b>Các nguyên tắc:</b></p>
 * <ul>
 *   <li>{@code traceId} được lấy từ header {@code X-Trace-Id} (do Gateway hoặc
 *       platform starter đặt) hoặc tự sinh UUID nếu header không tồn tại.</li>
 *   <li>Stack trace được ghi log ở phía server nhưng KHÔNG bao giờ trả về cho
 *       client để tránh lộ thông tin nội bộ.</li>
 *   <li>Lỗi 5xx được log ở mức ERROR, lỗi 4xx được log ở mức WARN.</li>
 *   <li>Mọi ngoại lệ không thuộc loại đã biết đều được ánh xạ thành {@code 500}
 *       với mã lỗi {@code "internal.error"}.</li>
 * </ul>
 *
 * @author Family Tree Platform Team
 */
@RestControllerAdvice
public class GlobalErrorHandler {

    /** Logger ghi nhận lỗi để phục vụ vận hành. */
    private static final Logger LOG = LoggerFactory.getLogger(GlobalErrorHandler.class);

    /**
     * Xử lý {@link DomainException} và các lớp con bằng cách ánh xạ trạng thái
     * và mã lỗi của chính ngoại lệ đó.
     *
     * @param ex  ngoại lệ nghiệp vụ được ném ra
     * @param req request HTTP hiện tại
     * @return {@link ResponseEntity} chứa {@link ErrorResponse}
     */
    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ErrorResponse> handleDomain(DomainException ex, HttpServletRequest req) {
        // Ủy quyền cho helper chung để đảm bảo logging và traceId được xử lý đồng nhất.
        return error(ex.getStatus(), ex.getCode(), ex.getMessage(), req, ex);
    }

    /**
     * Xử lý lỗi validation từ {@code @Valid} trên các request body.
     *
     * <p>Tập hợp các lỗi theo từng trường và đặt vào {@code details.fields}
     * của envelope lỗi.</p>
     *
     * @param ex  exception do Spring ném khi validation thất bại
     * @param req request HTTP hiện tại
     * @return {@link ResponseEntity} HTTP 400 với envelope lỗi
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        // Bước 1: Gom các lỗi theo từng trường vào một Map để client dễ xử lý.
        Map<String, Object> details = new HashMap<>();
        details.put("fields", ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        fe -> fe.getField(),
                        fe -> fe.getDefaultMessage() == null ? "invalid" : fe.getDefaultMessage(),
                        // Khi một trường có nhiều lỗi, giữ lại lỗi đầu tiên.
                        (a, b) -> a)));

        // Bước 2: Lấy traceId để hỗ trợ debug khi cần tra cứu log.
        String traceId = traceId(req);

        // Bước 3: Ghi log ở mức WARN vì đây là lỗi phía client (4xx).
        LOG.warn("Validation failed traceId={} details={}", traceId, details);

        // Bước 4: Trả về HTTP 400 với envelope lỗi ổn định.
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("validation.failed", "Request validation failed", traceId, details));
    }

    /**
     * Xử lý {@link OptimisticConcurrencyException}.
     *
     * @param ex  ngoại lệ xung đột phiên bản
     * @param req request HTTP hiện tại
     * @return {@link ResponseEntity} HTTP 409 với mã lỗi {@code "version.conflict"}
     */
    @ExceptionHandler(OptimisticConcurrencyException.class)
    public ResponseEntity<ErrorResponse> handleConcurrency(OptimisticConcurrencyException ex, HttpServletRequest req) {
        return error(HttpStatus.CONFLICT, "version.conflict", ex.getMessage(), req, ex);
    }

    /**
     * Xử lý {@link IdempotencyConflictException}.
     *
     * @param ex  ngoại lệ xung đột idempotency
     * @param req request HTTP hiện tại
     * @return {@link ResponseEntity} HTTP 409 với mã lỗi {@code "idempotency.conflict"}
     */
    @ExceptionHandler(IdempotencyConflictException.class)
    public ResponseEntity<ErrorResponse> handleIdempotency(IdempotencyConflictException ex, HttpServletRequest req) {
        return error(HttpStatus.CONFLICT, "idempotency.conflict", ex.getMessage(), req, ex);
    }

    /**
     * Xử lý {@link StaleProjectionException}.
     *
     * @param ex  ngoại lệ projection cũ
     * @param req request HTTP hiện tại
     * @return {@link ResponseEntity} HTTP 409 với mã lỗi {@code "projection.stale"}
     */
    @ExceptionHandler(StaleProjectionException.class)
    public ResponseEntity<ErrorResponse> handleStale(StaleProjectionException ex, HttpServletRequest req) {
        return error(HttpStatus.CONFLICT, "projection.stale", ex.getMessage(), req, ex);
    }

    /**
     * Xử lý {@link NotFoundException}.
     *
     * @param ex  ngoại lệ không tìm thấy tài nguyên
     * @param req request HTTP hiện tại
     * @return {@link ResponseEntity} HTTP 404 với mã lỗi {@code "not.found"}
     */
    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NotFoundException ex, HttpServletRequest req) {
        return error(HttpStatus.NOT_FOUND, "not.found", ex.getMessage(), req, ex);
    }

    /**
     * Xử lý {@link ForbiddenException}.
     *
     * @param ex  ngoại lệ bị từ chối quyền truy cập
     * @param req request HTTP hiện tại
     * @return {@link ResponseEntity} HTTP 403 với mã lỗi {@code "forbidden"}
     */
    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ErrorResponse> handleForbidden(ForbiddenException ex, HttpServletRequest req) {
        return error(HttpStatus.FORBIDDEN, "forbidden", ex.getMessage(), req, ex);
    }

    /**
     * Bộ xử lý "chốt chặn" cho mọi ngoại lệ chưa được khai báo cụ thể.
     *
     * <p>Mục đích: đảm bảo KHÔNG có ngoại lệ nào thoát ra khỏi controller mà
     * không được định dạng thành {@link ErrorResponse}. Điều này giúp bảo đảm
     * hợp đồng API đồng nhất và tránh lộ stack trace cho client.</p>
     *
     * @param ex  ngoại lệ bất kỳ chưa được xử lý
     * @param req request HTTP hiện tại
     * @return {@link ResponseEntity} HTTP 500 với mã lỗi {@code "internal.error"}
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleAny(Exception ex, HttpServletRequest req) {
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "internal.error", "An unexpected error occurred", req, ex);
    }

    /**
     * Helper dùng chung để dựng phản hồi lỗi với logging phù hợp.
     *
     * @param status  mã trạng thái HTTP sẽ trả về
     * @param code    mã lỗi ổn định theo máy
     * @param message thông điệp lỗi
     * @param req     request HTTP hiện tại
     * @param cause   ngoại lệ gốc để ghi log
     * @return {@link ResponseEntity} với envelope {@link ErrorResponse}
     */
    private static ResponseEntity<ErrorResponse> error(HttpStatus status, String code, String message,
                                                       HttpServletRequest req, Throwable cause) {
        // Bước 1: Lấy traceId từ request hoặc sinh mới.
        String traceId = traceId(req);

        // Bước 2: Phân loại log theo mức độ nghiêm trọng — lỗi server (5xx)
        // được log ở mức ERROR kèm stack trace, lỗi client (4xx) log ở WARN.
        if (status.is5xxServerError()) {
            LOG.error("Service error code={} traceId={}", code, traceId, cause);
        } else {
            LOG.warn("Client error code={} traceId={} message={}", code, traceId, message);
        }

        // Bước 3: Dựng phản hồi với envelope ổn định. details=null sẽ bị Jackson
        // bỏ qua nhờ @JsonInclude(NON_NULL) trên ErrorResponse.
        return ResponseEntity.status(status).body(new ErrorResponse(code, message, traceId, null));
    }

    /**
     * Lấy traceId từ header {@code X-Trace-Id} hoặc sinh UUID mới nếu header
     * không tồn tại.
     *
     * @param req request HTTP hiện tại
     * @return traceId có thể dùng để đối chiếu log
     */
    private static String traceId(HttpServletRequest req) {
        // Bước 1: Đọc header X-Trace-Id (do Gateway hoặc upstream đặt).
        String h = req.getHeader("X-Trace-Id");
        // Bước 2: Trả về giá trị đọc được hoặc UUID ngẫu nhiên.
        return h != null ? h : UUID.randomUUID().toString();
    }
}
