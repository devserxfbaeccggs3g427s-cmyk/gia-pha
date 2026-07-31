package com.familya.event.adapter.in.rest;

import com.familya.event.application.port.in.LoadEventCommand;
import com.familya.event.application.usecase.LoadEventUseCase;
import com.familya.event.domain.model.DomainEvent;
import com.familya.event.domain.model.RecurrenceRule;
import com.familya.platform.api.AsyncOperation;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * REST controller cho <b>di trú dữ liệu</b> — endpoint nội bộ phục vụ
 * luồng migration từ manifest blob sang cơ sở dữ liệu event-service.
 *
 * <h2>Endpoint</h2>
 * <ul>
 *   <li>{@code POST /api/v2/internal/event/migration/events}</li>
 * </ul>
 *
 * <p>Đây là endpoint <b>internal</b> (xem {@link com.familya.event.adapter.in.security.EventSecurityConfig}),
 * dùng cho migration job chạy giữa các hệ thống — không dành cho client UI.
 *
 * @author gia-pha platform
 */
@RestController
@RequestMapping(path = "/api/v2/internal/event", produces = MediaType.APPLICATION_JSON_VALUE)
public class MigrationController {

    private final LoadEventUseCase loader;
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Khởi tạo controller.
     *
     * @param loader use case {@link LoadEventUseCase}.
     */
    public MigrationController(LoadEventUseCase loader) {
        this.loader = loader;
    }

    /**
     * Nạp một sự kiện từ manifest.
     *
     * <p>Quy trình:
     * <ol>
     *   <li>Parse {@code recurrence} JSON thành {@link RecurrenceRule} (nếu có).</li>
     *   <li>Mặc định {@code createdAt} / {@code updatedAt} bằng {@link Instant#now()}
     *       nếu manifest không cung cấp — đảm bảo không có giá trị null.</li>
     *   <li>Chuyển {@code tombstoned} Boolean an toàn (hỗ trợ {@code null}).</li>
     *   <li>Trả về {@code 202 Accepted} với {@link AsyncOperation}.</li>
     * </ol>
     *
     * @param correlationId header {@code X-Correlation-Id} dùng cho truy vết.
     * @param req           payload yêu cầu — {@link LoadEventRequest}.
     * @return phản hồi chấp nhận.
     * @throws Exception nếu parse thất bại.
     */
    @PostMapping("/migration/events")
    public ResponseEntity<AsyncOperation> load(@RequestHeader("X-Correlation-Id") String correlationId,
                                                @Valid @RequestBody LoadEventRequest req) throws Exception {
        LoadEventUseCase.LoadResult r = loader.execute(new LoadEventCommand(
                req.eventId(), req.treeId(), req.title(), req.description(),
                DomainEvent.Kind.valueOf(req.kind()),
                req.startDate(), req.endDate(),
                parseRecurrence(req.recurrence()),
                req.primaryMemberId(),
                req.additionalMemberIds(), req.mediaRefs(), req.location(),
                req.createdAt() == null ? Instant.now() : req.createdAt(),
                req.updatedAt() == null ? Instant.now() : req.updatedAt(),
                Boolean.TRUE.equals(req.tombstoned()),
                req.replaySafe()));
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(AsyncOperation.accepted(r.eventId(), "/api/v2/operations/" + r.eventId()));
    }

    /**
     * Parse JSON {@code recurrence} thành {@link RecurrenceRule}. Tương tự
     * {@link DomainEventController#parseRecurrence(Object)} nhưng tách riêng
     * cho migration để tránh phụ thuộc vòng giữa các controller.
     *
     * @param payload payload JSON.
     * @return {@link RecurrenceRule} hoặc {@code null}.
     * @throws Exception nếu parse thất bại.
     */
    private RecurrenceRule parseRecurrence(Object payload) throws Exception {
        if (payload == null) return null;
        var m = mapper.convertValue(payload, new TypeReference<java.util.Map<String, Object>>() { });
        String freq = (String) m.get("frequency");
        int interval = ((Number) m.get("interval")).intValue();
        java.util.Map<String, Object> term = (java.util.Map<String, Object>) m.get("termination");
        RecurrenceRule.Termination t;
        if (term.containsKey("count")) t = new RecurrenceRule.Count(((Number) term.get("count")).intValue());
        else t = new RecurrenceRule.Until(LocalDate.parse((String) term.get("until")));
        return new RecurrenceRule(RecurrenceRule.Frequency.valueOf(freq), interval, t);
    }

    /**
     * Payload cho yêu cầu migration.
     *
     * @param eventId             định danh sự kiện (bắt buộc).
     * @param treeId              định danh cây (bắt buộc).
     * @param title               tiêu đề (bắt buộc).
     * @param description         mô tả.
     * @param kind                loại sự kiện (bắt buộc, chuỗi).
     * @param startDate           ngày bắt đầu.
     * @param endDate             ngày kết thúc.
     * @param recurrence          quy tắc lặp lại.
     * @param primaryMemberId     ID thành viên chính.
     * @param additionalMemberIds danh sách thành viên phụ.
     * @param mediaRefs           danh sách media.
     * @param location            địa điểm.
     * @param createdAt           thời điểm tạo ban đầu.
     * @param updatedAt           thời điểm cập nhật ban đầu.
     * @param tombstoned          cờ tombstone ban đầu.
     * @param replaySafe          cờ idempotent.
     */
    public record LoadEventRequest(
            @NotNull UUID eventId,
            @NotNull UUID treeId,
            @NotBlank String title,
            String description,
            @NotBlank String kind,
            LocalDate startDate,
            LocalDate endDate,
            Object recurrence,
            UUID primaryMemberId,
            List<UUID> additionalMemberIds,
            List<UUID> mediaRefs,
            String location,
            Instant createdAt,
            Instant updatedAt,
            Boolean tombstoned,
            boolean replaySafe) { }
}
