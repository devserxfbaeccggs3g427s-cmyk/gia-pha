/**
 * REST controller cho bề mặt vận hành (operator surface) của audit-ops.
 *
 * <p>Phân quyền được thực thi bởi
 * {@link com.familya.auditops.adapter.in.security.AuditOpsSecurityConfig}
 * thông qua Spring Security filter chain ở cấp URL. Mọi hành động của
 * operator đều được ghi vào audit log cùng với user id và lý do.</p>
 */
package com.familya.auditops.adapter.in.rest;

import com.familya.auditops.application.port.in.CancelOperationCommand;
import com.familya.auditops.application.port.in.OperatorRetryCommand;
import com.familya.auditops.application.usecase.AuditQueryService;
import com.familya.auditops.application.usecase.OperatorService;
import com.familya.auditops.domain.model.AuditEvent;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Cung cấp các endpoint:
 * <ul>
 *   <li>{@code POST /api/v2/audit-ops/operations/{id}/retry}</li>
 *   <li>{@code POST /api/v2/audit-ops/operations/{id}/cancel}</li>
 *   <li>{@code GET /api/v2/audit-ops/operations/{id}/audit}</li>
 * </ul>
 *
 * <p>Tất cả endpoint đều yêu cầu vai trò {@code AUDIT_OPERATOR} hoặc
 * {@code PLATFORM} (xem {@code AuditOpsSecurityConfig}).</p>
 */
@RestController
@RequestMapping(path = "/api/v2/audit-ops", produces = MediaType.APPLICATION_JSON_VALUE)
public class OperatorController {

    /** Service xử lý hành động của operator (retry, cancel). */
    private final OperatorService operatorService;
    /** Service truy vấn lịch sử audit. */
    private final AuditQueryService auditQuery;

    /**
     * Khởi tạo controller.
     *
     * @param operatorService service hành động operator
     * @param auditQuery      service truy vấn audit
     */
    public OperatorController(OperatorService operatorService, AuditQueryService auditQuery) {
        this.operatorService = operatorService;
        this.auditQuery = auditQuery;
    }

    /**
     * Endpoint retry một operation đang ở trạng thái {@code MANUAL_REVIEW}
     * hoặc {@code COMPENSATING}.
     *
     * @param operationId    id của operation cần retry
     * @param operatorUserId id của operator (header {@code X-Acting-User})
     * @param req            lý do retry
     * @param auth           {@link Authentication} chứa role của operator
     * @return {@code 202 Accepted} khi thành công
     */
    @PostMapping(path = "/operations/{operationId}/retry", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> retry(@PathVariable UUID operationId,
                                      @RequestHeader("X-Acting-User") UUID operatorUserId,
                                      @Valid @RequestBody RetryRequest req,
                                      Authentication auth) {
        // Ủy thác cho service kèm danh sách role để phục vụ kiểm tra quyền.
        operatorService.retry(new OperatorRetryCommand(operatorUserId, operationId, req.reason()),
                roles(auth));
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }

    /**
     * Endpoint huỷ một operation chưa kết thúc.
     *
     * @param operationId    id của operation cần huỷ
     * @param operatorUserId id của operator (header {@code X-Acting-User})
     * @param req            lý do huỷ
     * @param auth           {@link Authentication} chứa role của operator
     * @return {@code 202 Accepted} khi thành công
     */
    @PostMapping(path = "/operations/{operationId}/cancel", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> cancel(@PathVariable UUID operationId,
                                       @RequestHeader("X-Acting-User") UUID operatorUserId,
                                       @Valid @RequestBody CancelRequest req,
                                       Authentication auth) {
        operatorService.cancel(new CancelOperationCommand(operatorUserId, operationId, req.reason()),
                roles(auth));
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }

    /**
     * Trả về danh sách các sự kiện audit của một operation.
     *
     * @param operationId id operation
     * @param limit       số lượng tối đa (mặc định 100)
     * @return danh sách view các sự kiện audit
     */
    @GetMapping("/operations/{operationId}/audit")
    public ResponseEntity<List<Map<String, Object>>> audit(@PathVariable UUID operationId,
                                                           @RequestParam(defaultValue = "100") int limit) {
        List<AuditEvent> events = auditQuery.byOperation(operationId, limit);
        return ResponseEntity.ok(events.stream().map(OperatorController::toView).toList());
    }

    /**
     * Chuyển {@link AuditEvent} sang một {@link Map} để trả về JSON cho client.
     * Sử dụng {@link LinkedHashMap} để giữ thứ tự trường ổn định.
     *
     * @param e sự kiện audit
     * @return map view cho client
     */
    private static Map<String, Object> toView(AuditEvent e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("auditId", e.auditId());
        m.put("operationId", e.operationId());
        m.put("correlationId", e.correlationId());
        m.put("actorUserId", e.actorUserId());
        m.put("actorKind", e.actorKind().name());
        m.put("action", e.action());
        m.put("targetType", e.targetType());
        m.put("targetId", e.targetId());
        m.put("detail", e.detail());
        m.put("occurredAt", e.occurredAt());
        m.put("traceId", e.traceId());
        return m;
    }

    /**
     * Trích xuất danh sách role (authority) từ {@link Authentication}.
     *
     * @param auth đối tượng authentication của Spring Security
     * @return danh sách role; rỗng nếu auth null
     */
    private static List<String> roles(Authentication auth) {
        if (auth == null || auth.getAuthorities() == null) return List.of();
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());
    }

    /** DTO cho request body của endpoint retry. */
    public record RetryRequest(@NotBlank String reason) { }
    /** DTO cho request body của endpoint cancel. */
    public record CancelRequest(@NotBlank String reason) { }
}