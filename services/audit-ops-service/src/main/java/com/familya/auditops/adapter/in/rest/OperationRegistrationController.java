/**
 * Endpoint đăng ký operation công khai cho các dịch vụ chéo (cross-service).
 *
 * <p>Trả về envelope {@code 202 Accepted} theo yêu cầu của ADR-007.
 * Controller dùng chung {@code OperationController} của platform sẽ
 * phục vụ việc polling tại {@code GET /api/v2/operations/{id}}.</p>
 */
package com.familya.auditops.adapter.in.rest;

import com.familya.auditops.application.port.in.StartOperationCommand;
import com.familya.auditops.application.usecase.OperationService;
import com.familya.platform.api.AsyncOperation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

/**
 * REST controller cung cấp endpoint {@code POST /api/v2/audit-ops/operations}
 * để các service khác đăng ký một operation mới và nhận về envelope
 * {@link AsyncOperation}.
 *
 * <p>Controller <b>không</b> tự quyết định nghiệp vụ; nó chỉ chuyển đổi
 * HTTP request sang {@link StartOperationCommand} rồi ủy thác cho
 * {@link OperationService}.</p>
 */
@RestController
@RequestMapping(path = "/api/v2/audit-ops/operations", produces = MediaType.APPLICATION_JSON_VALUE)
public class OperationRegistrationController {

    /** Service nghiệp vụ để xử lý đăng ký. */
    private final OperationService service;

    /**
     * Khởi tạo controller.
     *
     * @param service service đăng ký operation
     */
    public OperationRegistrationController(OperationService service) {
        this.service = service;
    }

    /**
     * Xử lý yêu cầu {@code POST /api/v2/audit-ops/operations}.
     *
     * <p>Các bước xử lý:</p>
     * <ol>
     *   <li>Tạo {@link StartOperationCommand} từ header và body.</li>
     *   <li>Tính hash SHA-256 của payload để phục vụ idempotency.</li>
     *   <li>Gọi {@link OperationService#register} để tạo operation.</li>
     *   <li>Trả về {@code 202 Accepted} cùng header {@code Location} trỏ
     *       tới endpoint polling.</li>
     * </ol>
     *
     * @param actingUser     id người dùng thực hiện (từ {@code X-Acting-User})
     * @param idempotencyKey khoá idempotency (từ {@code Idempotency-Key})
     * @param correlationId  id tương quan (từ {@code X-Correlation-Id})
     * @param req            payload đăng ký
     * @return {@link ResponseEntity} với envelope {@link AsyncOperation}
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AsyncOperation> register(
            @RequestHeader(name = "X-Acting-User", required = false) UUID actingUser,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(name = "X-Correlation-Id", required = false) String correlationId,
            @Valid @RequestBody RegisterRequest req) {

        // Dựng command từ các header và body. Trường null được thay bằng giá trị mặc định an toàn.
        StartOperationCommand cmd = new StartOperationCommand(
                actingUser,
                req.operationType(),
                req.aggregateType(),
                req.aggregateId(),
                req.treeId(),
                req.targetRevision(),
                req.targetEpoch(),
                req.detail() == null ? Map.of() : req.detail(),
                idempotencyKey,
                sha256Hex(req),
                correlationId,
                req.startedAt() == null ? Instant.now() : req.startedAt());

        // Ủy thác cho service xử lý.
        AsyncOperation envelope = service.register(cmd);
        // Trả về 202 Accepted cùng header Location trỏ tới URL polling.
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .header("Location", "/api/v2/operations/" + envelope.operationId())
                .body(envelope);
    }

    /**
     * DTO cho request body của endpoint đăng ký.
     *
     * @param operationType  loại operation (bắt buộc)
     * @param aggregateType  loại aggregate liên quan
     * @param aggregateId    id của aggregate
     * @param treeId         id cây gia phả (nếu có)
     * @param targetRevision revision mục tiêu
     * @param targetEpoch    epoch mục tiêu
     * @param detail         thông tin bổ sung dạng bản đồ
     * @param startedAt      thời điểm bắt đầu phía client (nếu có)
     */
    public record RegisterRequest(
            @NotBlank String operationType,
            String aggregateType,
            String aggregateId,
            UUID treeId,
            Long targetRevision,
            Long targetEpoch,
            Map<String, Object> detail,
            Instant startedAt) { }

    /**
     * Tính hash SHA-256 của một số trường quan trọng trong request để phục vụ
     * idempotency. Chỉ hash các trường ảnh hưởng tới nội dung nghiệp vụ
     * (operationType, aggregateId, targetRevision, targetEpoch).
     *
     * @param r đối tượng request
     * @return chuỗi hex biểu diễn hash
     * @throws IllegalStateException nếu SHA-256 không khả dụng trên JVM
     */
    private static String sha256Hex(RegisterRequest r) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            // Nối các trường bằng ký tự phân cách để tránh va chạm (collision).
            String body = (r.operationType() == null ? "" : r.operationType()) + "|"
                    + (r.aggregateId() == null ? "" : r.aggregateId()) + "|"
                    + (r.targetRevision() == null ? "" : r.targetRevision()) + "|"
                    + (r.targetEpoch() == null ? "" : r.targetEpoch());
            byte[] hash = md.digest(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // Không xảy ra với JVM tiêu chuẩn nhưng vẫn bắt để đảm bảo an toàn.
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}