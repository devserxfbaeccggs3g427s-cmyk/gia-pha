package com.familya.relationship.adapter.in.rest;

import com.familya.platform.api.AsyncOperation;
import com.familya.relationship.application.port.in.LoadRelationshipManifestCommand;
import com.familya.relationship.application.usecase.LoadRelationshipManifestUseCase;
import com.familya.relationship.domain.model.Relationship;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Controller REST phục vụ <b>migration dữ liệu</b>: nạp manifest các quan hệ
 * từ hệ thống cũ (hoặc từ bản backup) vào Relationship service.
 * <p>
 * Endpoint: {@code POST /api/v2/internal/relationship/migration/relationships}.
 * </p>
 *
 * <h2>Bảo mật</h2>
 * <p>
 * Endpoint thuộc nhóm {@code /api/v2/internal/**} - trong môi trường production,
 * gateway/API chỉ nên mở endpoint này cho các caller nội bộ đã được phép.
 * </p>
 */
@RestController
@RequestMapping(path = "/api/v2/internal/relationship", produces = MediaType.APPLICATION_JSON_VALUE)
public class MigrationController {

    /** Use case nạp manifest. */
    private final LoadRelationshipManifestUseCase loader;

    /**
     * Khởi tạo controller.
     *
     * @param loader use case nạp manifest
     */
    public MigrationController(LoadRelationshipManifestUseCase loader) {
        this.loader = loader;
    }

    /**
     * Nhận một manifest các quan hệ và nạp vào DB.
     * <p>
     * Header yêu cầu: {@code X-Correlation-Id} (dùng cho logging/audit).
     * </p>
     * <p>
     * Quy trình:
     * </p>
     * <ol>
     *   <li>Ánh xạ từ DTO {@link RelationshipLineDto} sang {@code RelationshipLine}
     *       của use case.</li>
     *   <li>Nếu {@code createdAt} bị thiếu thì gán {@code Instant.now()} để đảm
     *       bảo dữ liệu không null.</li>
     *   <li>Ủy quyền cho use case.</li>
     *   <li>Trả về {@link AsyncOperation} với {@code 202 ACCEPTED}.</li>
     * </ol>
     *
     * @param correlationId mã tương quan (cho logging)
     * @param req           manifest các quan hệ
     * @return {@code 202 ACCEPTED} cùng {@link AsyncOperation}
     */
    @PostMapping("/migration/relationships")
    public ResponseEntity<AsyncOperation> load(@RequestHeader("X-Correlation-Id") String correlationId,
                                                @Valid @RequestBody LoadManifestRequest req) {
        // Ánh xạ từ DTO sang domain line, đồng thời xử lý giá trị createdAt mặc định.
        LoadRelationshipManifestUseCase.LoadResult r = loader.execute(new LoadRelationshipManifestCommand(
                req.treeId(),
                req.relationships().stream().map(l -> new LoadRelationshipManifestCommand.RelationshipLine(
                        l.relationshipId(), Relationship.Kind.valueOf(l.kind()),
                        l.fromMemberId(), l.toMemberId(), l.metadataJson(),
                        l.revision(), l.createdAt() == null ? Instant.now() : l.createdAt())).toList(),
                req.replaySafe()));
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(AsyncOperation.accepted(UUID.randomUUID(), "/api/v2/operations/" + UUID.randomUUID()));
    }

    /**
     * DTO cho manifest gồm danh sách các dòng quan hệ.
     *
     * @param treeId       định danh cây gia phả đích
     * @param relationships danh sách các dòng quan hệ
     * @param replaySafe   cờ chạy lại an toàn
     */
    public record LoadManifestRequest(@NotNull UUID treeId, @NotNull List<RelationshipLineDto> relationships, boolean replaySafe) { }

    /**
     * DTO cho một dòng trong manifest.
     *
     * @param relationshipId định danh quan hệ
     * @param kind           loại quan hệ
     * @param fromMemberId   thành viên phía nguồn
     * @param toMemberId     thành viên phía đích
     * @param metadataJson   chuỗi JSON metadata
     * @param revision       số hiệu chỉnh sửa
     * @param createdAt      thời điểm tạo
     */
    public record RelationshipLineDto(
            @NotNull UUID relationshipId,
            @NotNull String kind,
            @NotNull UUID fromMemberId,
            @NotNull UUID toMemberId,
            String metadataJson,
            @NotNull Long revision,
            Instant createdAt) { }
}