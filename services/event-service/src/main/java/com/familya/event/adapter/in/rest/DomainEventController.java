package com.familya.event.adapter.in.rest;

import com.familya.event.application.port.in.CreateDomainEventCommand;
import com.familya.event.application.port.in.TombstoneDomainEventCommand;
import com.familya.event.application.port.in.UpdateDomainEventCommand;
import com.familya.event.application.usecase.CreateDomainEventUseCase;
import com.familya.event.application.usecase.QueryDomainEventUseCase;
import com.familya.event.application.usecase.TombstoneDomainEventUseCase;
import com.familya.event.application.usecase.UpdateDomainEventUseCase;
import com.familya.event.domain.model.DomainEvent;
import com.familya.event.domain.model.RecurrenceRule;
import com.familya.platform.api.AsyncOperation;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * REST controller cho tài nguyên {@code DomainEvent} của một cây gia phả.
 *
 * <h2>Endpoint</h2>
 * <ul>
 *   <li>{@code POST   /api/v2/trees/{treeId}/events}          — tạo mới.</li>
 *   <li>{@code PUT    /api/v2/trees/{treeId}/events/{eventId}} — cập nhật.</li>
 *   <li>{@code DELETE /api/v2/trees/{treeId}/events/{eventId}} — tombstone.</li>
 *   <li>{@code GET    /api/v2/trees/{treeId}/events/{eventId}} — truy vấn.</li>
 * </ul>
 *
 * <h2>Header quan trọng</h2>
 * <ul>
 *   <li>{@code X-Acting-User}: UUID người dùng thực hiện — bắt buộc.</li>
 *   <li>{@code X-Tree-Revision}: revision cây kỳ vọng — phục vụ kiểm
 *       tra stale projection.</li>
 *   <li>{@code If-Match}: phiên bản aggregate kỳ vọng (optimistic
 *       concurrency).</li>
 * </ul>
 *
 * <p>Mọi thao tác mutation trả về {@code 202 Accepted} cùng
 * {@link AsyncOperation} để client có thể theo dõi tiến trình qua
 * endpoint {@code /api/v2/operations/{id}}.
 *
 * @author gia-pha platform
 */
@RestController
@RequestMapping(path = "/api/v2/trees/{treeId}/events", produces = MediaType.APPLICATION_JSON_VALUE)
public class DomainEventController {

    private final CreateDomainEventUseCase createEvent;
    private final UpdateDomainEventUseCase updateEvent;
    private final TombstoneDomainEventUseCase tombstoneEvent;
    private final QueryDomainEventUseCase query;
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Khởi tạo controller.
     *
     * @param createEvent    use case tạo mới.
     * @param updateEvent    use case cập nhật.
     * @param tombstoneEvent use case tombstone.
     * @param query          use case truy vấn.
     */
    public DomainEventController(CreateDomainEventUseCase createEvent,
                                  UpdateDomainEventUseCase updateEvent,
                                  TombstoneDomainEventUseCase tombstoneEvent,
                                  QueryDomainEventUseCase query) {
        this.createEvent = createEvent;
        this.updateEvent = updateEvent;
        this.tombstoneEvent = tombstoneEvent;
        this.query = query;
    }

    /**
     * Tạo mới một sự kiện. Trả về {@code 202 Accepted} cùng
     * {@link AsyncOperation}.
     *
     * @param actingUser           người dùng thực hiện (header {@code X-Acting-User}).
     * @param treeId               định danh cây.
     * @param expectedTreeRevision revision cây kỳ vọng (header {@code X-Tree-Revision}).
     * @param req                  payload yêu cầu — {@link CreateEventRequest}.
     * @return {@code 202 Accepted} với header {@code Location} và body
     *         {@link AsyncOperation}.
     * @throws Exception nếu parse {@code recurrence} thất bại.
     */
    @PostMapping
    public ResponseEntity<AsyncOperation> create(@RequestHeader("X-Acting-User") UUID actingUser,
                                                  @PathVariable UUID treeId,
                                                  @RequestHeader(value = "X-Tree-Revision", required = false) Long expectedTreeRevision,
                                                  @Valid @RequestBody CreateEventRequest req) throws Exception {
        // Mặc định revision = 0 khi header không được gửi — dùng để
        // "chấp nhận mọi revision hiện tại" (kịch bản nội bộ).
        long er = expectedTreeRevision == null ? 0L : expectedTreeRevision;

        UUID id = createEvent.execute(new CreateDomainEventCommand(
                treeId, actingUser, req.title(), req.description(),
                DomainEvent.Kind.valueOf(req.kind()),
                req.startDate(), req.endDate(),
                parseRecurrence(req.recurrence()),
                req.primaryMemberId(),
                req.additionalMemberIds(), req.mediaRefs(), req.location(), er));

        // Trả về 202 + Location để client có thể theo dõi.
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .header("Location", "/api/v2/trees/" + treeId + "/events/" + id)
                .body(AsyncOperation.accepted(id, "/api/v2/operations/" + id));
    }

    /**
     * Cập nhật nội dung một sự kiện (qua {@code PUT}). Sử dụng
     * {@code If-Match} cho optimistic concurrency và {@code X-Tree-Revision}
     * cho projection freshness.
     *
     * @param actingUser           người dùng thực hiện.
     * @param treeId               định danh cây.
     * @param eventId              định danh sự kiện.
     * @param expectedVersion      phiên bản aggregate kỳ vọng (header {@code If-Match}).
     * @param expectedTreeRevision revision cây kỳ vọng (header {@code X-Tree-Revision}).
     * @param req                  payload yêu cầu — {@link UpdateEventRequest}.
     * @return {@code 202 Accepted} với body {@link AsyncOperation}.
     * @throws Exception nếu parse {@code recurrence} thất bại.
     */
    @PutMapping("/{eventId}")
    public ResponseEntity<AsyncOperation> update(@RequestHeader("X-Acting-User") UUID actingUser,
                                                  @PathVariable UUID treeId,
                                                  @PathVariable UUID eventId,
                                                  @RequestHeader(value = "If-Match", required = false) Long expectedVersion,
                                                  @RequestHeader(value = "X-Tree-Revision", required = false) Long expectedTreeRevision,
                                                  @Valid @RequestBody UpdateEventRequest req) throws Exception {
        // Mặc định 0 cho cả hai header optional.
        long ev = expectedVersion == null ? 0L : expectedVersion;
        long er = expectedTreeRevision == null ? 0L : expectedTreeRevision;

        updateEvent.execute(new UpdateDomainEventCommand(
                eventId, actingUser, ev, er,
                req.title(), req.description(),
                DomainEvent.Kind.valueOf(req.kind()),
                req.startDate(), req.endDate(),
                parseRecurrence(req.recurrence()),
                req.primaryMemberId(),
                req.additionalMemberIds(), req.mediaRefs(), req.location()));

        return ResponseEntity.accepted().body(AsyncOperation.accepted(eventId, "/api/v2/operations/" + eventId));
    }

    /**
     * Tombstone (xóa mềm) một sự kiện.
     *
     * @param actingUser           người dùng thực hiện.
     * @param treeId               định danh cây.
     * @param eventId              định danh sự kiện.
     * @param expectedVersion      phiên bản aggregate kỳ vọng (header {@code If-Match}).
     * @param expectedTreeRevision revision cây kỳ vọng.
     * @return {@code 202 Accepted}.
     */
    @DeleteMapping("/{eventId}")
    public ResponseEntity<AsyncOperation> tombstone(@RequestHeader("X-Acting-User") UUID actingUser,
                                                     @PathVariable UUID treeId,
                                                     @PathVariable UUID eventId,
                                                     @RequestHeader(value = "If-Match", required = false) Long expectedVersion,
                                                     @RequestHeader(value = "X-Tree-Revision", required = false) Long expectedTreeRevision) {
        long ev = expectedVersion == null ? 0L : expectedVersion;
        long er = expectedTreeRevision == null ? 0L : expectedTreeRevision;

        tombstoneEvent.execute(new TombstoneDomainEventCommand(eventId, actingUser, ev, er));

        return ResponseEntity.accepted().body(AsyncOperation.accepted(eventId, "/api/v2/operations/" + eventId));
    }

    /**
     * Truy vấn một sự kiện theo ID. Bao gồm cả danh sách <i>tham chiếu
     * đứt</i> để client có thể cảnh báo người dùng.
     *
     * @param treeId  định danh cây (chỉ để ràng buộc URL).
     * @param eventId định danh sự kiện.
     * @return {@link EventResponse} chứa aggregate và danh sách dangling.
     */
    @GetMapping("/{eventId}")
    public ResponseEntity<EventResponse> get(@PathVariable UUID treeId, @PathVariable UUID eventId) {
        DomainEvent ev = query.findById(eventId).orElseThrow(() ->
                new com.familya.platform.error.NotFoundException("Event " + eventId + " not found"));
        return ResponseEntity.ok(EventResponse.from(ev, query.danglingReferences(ev)));
    }

    /**
     * Parse JSON {@code recurrence} thành {@link RecurrenceRule}.
     *
     * <p>Quy trình:
     * <ol>
     *   <li>Nếu payload {@code null} → trả về {@code null}.</li>
     *   <li>Convert qua {@link ObjectMapper} sang {@link java.util.Map}.</li>
     *   <li>Trích {@code frequency}, {@code interval}, {@code termination}
     *       và khởi tạo object tương ứng.</li>
     * </ol>
     *
     * @param payload payload JSON thô từ request.
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
     * Payload cho yêu cầu tạo mới sự kiện.
     *
     * @param title               tiêu đề (bắt buộc).
     * @param description         mô tả.
     * @param kind                loại sự kiện (bắt buộc, dạng chuỗi).
     * @param startDate           ngày bắt đầu.
     * @param endDate             ngày kết thúc.
     * @param recurrence          quy tắc lặp lại (JSON thô).
     * @param primaryMemberId     ID thành viên chính.
     * @param additionalMemberIds danh sách thành viên phụ.
     * @param mediaRefs           danh sách media.
     * @param location            địa điểm.
     */
    public record CreateEventRequest(
            @NotBlank String title,
            String description,
            @NotBlank String kind,
            LocalDate startDate,
            LocalDate endDate,
            Object recurrence,
            UUID primaryMemberId,
            List<UUID> additionalMemberIds,
            List<UUID> mediaRefs,
            String location) { }

    /**
     * Payload cho yêu cầu cập nhật sự kiện (không bắt buộc các trường —
     * null nghĩa là giữ nguyên).
     *
     * @param title               tiêu đề mới.
     * @param description         mô tả mới.
     * @param kind                loại mới.
     * @param startDate           ngày bắt đầu mới.
     * @param endDate             ngày kết thúc mới.
     * @param recurrence          quy tắc lặp lại mới.
     * @param primaryMemberId     ID thành viên chính mới.
     * @param additionalMemberIds danh sách thành viên phụ mới.
     * @param mediaRefs           danh sách media mới.
     * @param location            địa điểm mới.
     */
    public record UpdateEventRequest(
            String title,
            String description,
            String kind,
            LocalDate startDate,
            LocalDate endDate,
            Object recurrence,
            UUID primaryMemberId,
            List<UUID> additionalMemberIds,
            List<UUID> mediaRefs,
            String location) { }

    /**
     * Phản hồi cho truy vấn sự kiện.
     *
     * @param id                  định danh.
     * @param treeId              định danh cây.
     * @param title               tiêu đề.
     * @param description         mô tả.
     * @param kind                loại sự kiện (chuỗi).
     * @param startDate           ngày bắt đầu.
     * @param endDate             ngày kết thúc.
     * @param recurrence          quy tắc lặp lại dạng chuỗi (mặc định {@code null} — chưa serialize).
     * @param primaryMemberId     ID thành viên chính.
     * @param additionalMemberIds danh sách thành viên phụ.
     * @param mediaRefs           danh sách media.
     * @param location            địa điểm.
     * @param danglingReferences  tập tham chiếu đứt (để client cảnh báo).
     * @param version             phiên bản.
     * @param tombstoned          cờ tombstone.
     */
    public record EventResponse(UUID id, UUID treeId, String title, String description, String kind,
                                 LocalDate startDate, LocalDate endDate, String recurrence,
                                 UUID primaryMemberId, List<UUID> additionalMemberIds, List<UUID> mediaRefs,
                                 String location, java.util.Set<UUID> danglingReferences,
                                 long version, boolean tombstoned) {
        /**
         * Tạo {@link EventResponse} từ aggregate và tập dangling.
         *
         * @param ev       aggregate.
         * @param dangling tập tham chiếu đứt (từ
         *                 {@link com.familya.event.application.usecase.QueryDomainEventUseCase#danglingReferences}).
         * @return response đã chuyển đổi.
         */
        public static EventResponse from(DomainEvent ev, java.util.Set<UUID> dangling) {
            return new EventResponse(ev.id(), ev.treeId(), ev.title(), ev.description(), ev.kind().name(),
                    ev.startDate(), ev.endDate(), null,
                    ev.primaryMemberId(), ev.additionalMemberIds(), ev.mediaRefs(),
                    ev.location(), dangling, ev.version(), ev.isTombstoned());
        }
    }
}
