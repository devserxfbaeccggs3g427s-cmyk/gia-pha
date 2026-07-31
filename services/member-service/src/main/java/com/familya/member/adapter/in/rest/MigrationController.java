package com.familya.member.adapter.in.rest;

import com.familya.member.application.port.in.LoadMemberCommand;
import com.familya.member.application.usecase.LoadMemberUseCase;
import com.familya.platform.api.AsyncOperation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * REST controller phục vụ việc di trú (migration) dữ liệu thành viên từ hệ thống cũ.
 * Endpoint này được gọi nội bộ bởi pipeline di trú và được bảo vệ bởi {@code MemberSecurityConfig}.
 */
@RestController
@RequestMapping(path = "/api/v2/internal/member", produces = MediaType.APPLICATION_JSON_VALUE)
public class MigrationController {

    private final LoadMemberUseCase loader;

    /**
     * Khởi tạo controller với use case tải thành viên.
     *
     * @param loader use case {@link LoadMemberUseCase}
     */
    public MigrationController(LoadMemberUseCase loader) {
        this.loader = loader;
    }

    /**
     * Nạp một thành viên từ manifest di trú vào CSDL. Endpoint idempotent: gọi lại với cùng
     * {@code memberId} sẽ trả về {@code DUPLICATE} mà không tạo bản ghi mới.
     *
     * @param correlationId mã tương quan cho truy vết pipeline (header {@code X-Correlation-Id})
     * @param req           payload chứa thông tin thành viên cần nạp
     * @return phản hồi HTTP 202 Accepted kèm {@link AsyncOperation}
     */
    @PostMapping("/migration/members")
    public ResponseEntity<AsyncOperation> load(@RequestHeader("X-Correlation-Id") String correlationId,
                                               @Valid @RequestBody LoadMemberRequest req) {
        LoadMemberUseCase.LoadResult r = loader.execute(new LoadMemberCommand(
                req.memberId(), req.treeId(), req.userId(),
                req.displayName(), req.givenName(), req.surname(),
                req.birthDate(), req.deathDate(),
                Boolean.TRUE.equals(req.birthYearKnown()), Boolean.TRUE.equals(req.deathYearKnown()),
                req.gender(), req.status() == null ? "LIVING" : req.status(),
                req.generation(), req.legacyAvatarUrl(), req.notes(),
                req.createdAt() == null ? Instant.now() : req.createdAt(),
                req.updatedAt() == null ? Instant.now() : req.updatedAt(),
                Boolean.TRUE.equals(req.tombstoned()),
                req.replaySafe()));
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(AsyncOperation.accepted(r.memberId(), "/api/v2/operations/" + r.memberId()));
    }

    /**
     * Payload yêu cầu nạp thành viên từ manifest di trú.
     *
     * @param memberId        mã thành viên gốc (bắt buộc)
     * @param treeId          mã cây (bắt buộc)
     * @param userId          mã người dùng hệ thống
     * @param displayName     tên hiển thị (bắt buộc)
     * @param givenName       tên
     * @param surname         họ
     * @param birthDate       ngày sinh
     * @param deathDate       ngày mất
     * @param birthYearKnown  năm sinh đã biết chính xác
     * @param deathYearKnown  năm mất đã biết chính xác
     * @param gender          giới tính
     * @param status          trạng thái (mặc định LIVING)
     * @param generation      thế hệ
     * @param legacyAvatarUrl URL ảnh cũ
     * @param notes           ghi chú
     * @param createdAt       thời điểm tạo gốc (bảo toàn)
     * @param updatedAt       thời điểm cập nhật gốc
     * @param tombstoned      cờ đánh dấu đã tombstone hay chưa
     * @param replaySafe      cờ đảm bảo idempotency khi phát lại
     */
    public record LoadMemberRequest(
            @NotNull UUID memberId,
            @NotNull UUID treeId,
            UUID userId,
            @NotBlank String displayName,
            String givenName,
            String surname,
            LocalDate birthDate,
            LocalDate deathDate,
            Boolean birthYearKnown,
            Boolean deathYearKnown,
            String gender,
            String status,
            Integer generation,
            String legacyAvatarUrl,
            String notes,
            Instant createdAt,
            Instant updatedAt,
            Boolean tombstoned,
            boolean replaySafe) { }
}