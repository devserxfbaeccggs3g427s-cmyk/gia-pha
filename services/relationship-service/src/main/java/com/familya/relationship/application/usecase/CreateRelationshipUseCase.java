package com.familya.relationship.application.usecase;

import com.familya.relationship.application.port.in.CreateRelationshipCommand;
import com.familya.relationship.application.port.out.AuthorizationProjectionRepository;
import com.familya.relationship.application.port.out.MemberExistenceProjection;
import com.familya.relationship.application.port.out.RelationshipEventPublisher;
import com.familya.relationship.application.port.out.RelationshipRepository;
import com.familya.relationship.domain.event.RelationshipCreated;
import com.familya.relationship.domain.exception.DanglingMemberReferenceException;
import com.familya.relationship.domain.exception.DuplicateRelationshipException;
import com.familya.relationship.domain.graph.GraphAlgorithms;
import com.familya.relationship.domain.model.Relationship;
import com.familya.platform.error.ForbiddenException;
import com.familya.platform.error.StaleProjectionException;
import com.familya.platform.telemetry.PlatformMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Use case tạo mới một cạnh (quan hệ) trong đồ thị gia phả.
 * <p>
 * Quy trình xử lý gồm các bước theo thứ tự:
 * </p>
 * <ol>
 *   <li><b>Đo lường (metrics)</b> - ghi nhận mutation được chấp nhận để theo
 *       dõi SLO và phát hiện bất thường.</li>
 *   <li><b>Phân quyền</b> - tra cứu projection phân quyền cho cặp
 *       {@code (treeId, actingUser)}; từ chối nếu không có, đã thu hồi, hoặc
 *       không đủ quyền chỉnh sửa.</li>
 *   <li><b>Kiểm tra stale projection</b> - đảm bảo client nhìn thấy phiên bản
 *       phân quyền mới nhất mà họ kỳ vọng.</li>
 *   <li><b>Kiểm tra tồn tại thành viên</b> - cả {@code fromMemberId} và
 *       {@code toMemberId} phải tồn tại và chưa bị tombstone trong
 *       {@code MemberExistenceProjection}.</li>
 *   <li><b>Sinh số thứ tự lệnh</b> - lấy {@code commandSeq} tiếp theo của cây
 *       (trong transaction với {@code SELECT FOR UPDATE}).</li>
 *   <li><b>Kiểm tra trùng lặp</b> - đảm bảo chưa tồn tại cạnh trùng bộ bốn.</li>
 *   <li><b>Kiểm tra chu trình</b> - chỉ với {@code PARENT_CHILD}, đảm bảo cạnh
 *       mới không tạo vòng.</li>
 *   <li><b>Chèn quan hệ</b> - với quan hệ {@code SPOUSE}, chèn thêm cạnh
 *       đối xứng (mirror) trong cùng transaction.</li>
 *   <li><b>Ghi command log</b> - đảm bảo khả năng tái dựng.</li>
 *   <li><b>Phát sự kiện</b> - thông qua outbox (commit cùng transaction).</li>
 * </ol>
 *
 * <p>
 * Toàn bộ chuỗi thao tác trên diễn ra trong một transaction duy nhất nhờ
 * annotation {@code @Transactional}, đảm bảo quan hệ, command log và outbox
 * được commit hoặc rollback đồng thời. Điều này cho phép projection rebuild
 * và emergency reconciliation dựa vào command log là nguồn sự thật.
 * </p>
 */
@Service
public class CreateRelationshipUseCase {

    /** Logger dùng để ghi nhận hoạt động của use case. */
    private static final Logger LOG = LoggerFactory.getLogger(CreateRelationshipUseCase.class);

    /** Repository để truy cập dữ liệu quan hệ và command log. */
    private final RelationshipRepository repo;
    /** Cổng phát sự kiện miền (qua outbox). */
    private final RelationshipEventPublisher publisher;
    /** Projection để kiểm tra thành viên tồn tại. */
    private final MemberExistenceProjection memberProj;
    /** Projection phân quyền (đọc từ bảng nội bộ). */
    private final AuthorizationProjectionRepository authRepo;
    /** Bộ đếm metric của platform. */
    private final PlatformMetrics metrics;
    /** Đồng hồ tiêm vào để dễ kiểm thử (mặc định {@code Instant::now}). */
    private final Clock clock;

    /**
     * Khởi tạo use case với đầy đủ phụ thuộc.
     *
     * @param repo        repository quan hệ
     * @param publisher   cổng phát sự kiện
     * @param memberProj  projection thành viên
     * @param authRepo    projection phân quyền
     * @param metrics     bộ đếm metric
     * @param clock       đồng hồ tiêm vào (dùng để test)
     */
    public CreateRelationshipUseCase(RelationshipRepository repo, RelationshipEventPublisher publisher,
                                     MemberExistenceProjection memberProj,
                                     AuthorizationProjectionRepository authRepo,
                                     PlatformMetrics metrics, Clock clock) {
        this.repo = repo;
        this.publisher = publisher;
        this.memberProj = memberProj;
        this.authRepo = authRepo;
        this.metrics = metrics;
        this.clock = clock;
    }

    /**
     * Thực thi lệnh tạo quan hệ.
     *
     * @param cmd lệnh tạo quan hệ (record bất biến)
     * @return định danh của quan hệ vừa được tạo
     * @throws ForbiddenException                nếu user không có quyền
     * @throws StaleProjectionException          nếu projection phân quyền cũ
     * @throws DanglingMemberReferenceException  nếu một thành viên không tồn tại
     * @throws DuplicateRelationshipException    nếu cạnh đã tồn tại
     * @throws com.familya.relationship.domain.exception.CycleDetectedException
     *                                           nếu cạnh mới tạo chu trình
     */
    @Transactional
    public UUID execute(CreateRelationshipCommand cmd) {
        // Bước 1: ghi nhận metric "mutation được chấp nhận" (trước khi xác thực)
        // để có thể phát hiện sớm tình trạng traffic bất thường.
        metrics.mutationAccepted("relationship-service", "createRelationship");

        // Bước 2: phân quyền - tra projection phân quyền cho (treeId, actingUser).
        // Nếu không có dòng nào nghĩa là user không từng được cấp quyền -> từ chối.
        var auth = authRepo.findAuth(cmd.treeId(), cmd.actingUser())
                .orElseThrow(() -> new ForbiddenException(
                        "No authorization projection for tree=" + cmd.treeId() + " user=" + cmd.actingUser()));
        // Nếu dòng đã bị thu hồi hoặc vai trò không đủ quyền edit -> từ chối.
        if (auth.isRevoked() || !auth.canEdit()) {
            throw new ForbiddenException("User " + cmd.actingUser() + " cannot edit tree " + cmd.treeId());
        }
        // Nếu revision của projection cũ hơn client kỳ vọng -> stale -> từ chối,
        // buộc client phải đồng bộ lại projection trước khi thử lại.
        if (auth.revision() < cmd.expectedTreeRevision()) {
            throw new StaleProjectionException(
                    "Auth projection revision " + auth.revision() + " < expected " + cmd.expectedTreeRevision());
        }

        // Bước 3: kiểm tra tồn tại của cả hai thành viên tham gia cạnh.
        // Từ chối nếu một trong hai đầu không có hoặc đã bị tombstone.
        if (!memberProj.isAvailable(cmd.treeId(), cmd.fromMemberId())) {
            throw new DanglingMemberReferenceException(
                    "fromMemberId " + cmd.fromMemberId() + " is missing or tombstoned");
        }
        if (!memberProj.isAvailable(cmd.treeId(), cmd.toMemberId())) {
            throw new DanglingMemberReferenceException(
                    "toMemberId " + cmd.toMemberId() + " is missing or tombstoned");
        }

        // Bước 4: lấy số thứ tự lệnh tiếp theo cho cây (per-tree monotonic).
        // Việc này đảm bảo các lệnh cùng cây được xếp hàng nối tiếp, tránh xung đột.
        long seq = repo.nextCommandSeq(cmd.treeId());

        // Bước 5: kiểm tra trùng lặp cạnh - bộ bốn (tree, kind, from, to) phải duy nhất.
        if (repo.existsEdge(cmd.treeId(), cmd.kind(), cmd.fromMemberId(), cmd.toMemberId())) {
            throw new DuplicateRelationshipException(
                    "Duplicate " + cmd.kind() + " edge " + cmd.fromMemberId() + " -> " + cmd.toMemberId());
        }

        // Bước 6: kiểm tra chu trình (chỉ áp dụng cho PARENT_CHILD - loại quan hệ
        // duy nhất tạo thành cấu trúc cây; SPOUSE và ADOPTION không tạo vòng).
        if (cmd.kind() == Relationship.Kind.PARENT_CHILD) {
            var existing = repo.listByTree(cmd.treeId(), false);
            GraphAlgorithms.assertNoCycle(existing, cmd.fromMemberId(), cmd.toMemberId());
        }

        // Bước 7: khởi tạo aggregate Relationship và chèn vào DB.
        Instant now = clock.now();
        UUID id = UUID.randomUUID();
        Relationship rel = new Relationship(id, cmd.treeId(), cmd.kind(),
                cmd.fromMemberId(), cmd.toMemberId(), cmd.metadataJson(),
                seq, now, null, 0L);
        repo.insert(rel);

        // Bước 7b: với quan hệ SPOUSE, cạnh được lưu đối xứng. Chèn thêm mirror
        // để mọi truy vấn đều nhìn thấy quan hệ theo cả hai chiều.
        if (cmd.kind() == Relationship.Kind.SPOUSE) {
            UUID mirrorId = UUID.randomUUID();
            Relationship mirror = new Relationship(mirrorId, cmd.treeId(), Relationship.Kind.SPOUSE,
                    cmd.toMemberId(), cmd.fromMemberId(), cmd.metadataJson(),
                    seq, now, null, 0L);
            repo.insert(mirror);
        }

        // Bước 8: ghi command log để projection rebuild có thể tái dựng.
        repo.appendCommandLog(cmd.treeId(), seq, "create_relationship",
                cmd.actingUser(), payloadHash(cmd), now);

        // Bước 9: phát sự kiện RelationshipCreated thông qua outbox.
        // Outbox đảm bảo sự kiện chỉ được xuất bản khi transaction commit.
        publisher.publish(new RelationshipCreated(id, cmd.treeId(), cmd.kind(),
                cmd.fromMemberId(), cmd.toMemberId(), cmd.metadataJson(), seq, now));
        // Với SPOUSE, phát thêm một sự kiện cho cạnh đối xứng (cùng commandSeq).
        if (cmd.kind() == Relationship.Kind.SPOUSE) {
            publisher.publish(new RelationshipCreated(UUID.randomUUID(), cmd.treeId(),
                    Relationship.Kind.SPOUSE, cmd.toMemberId(), cmd.fromMemberId(),
                    cmd.metadataJson(), seq, now));
        }
        LOG.info("Created relationship id={} kind={} {} -> {} seq={} actingUser={}",
                id, cmd.kind(), cmd.fromMemberId(), cmd.toMemberId(), seq, cmd.actingUser());
        return id;
    }

    /**
     * Tính mã băm tóm tắt của payload lệnh để phục vụ việc kiểm tra toàn vẹn
     * trong command log. Sử dụng {@code hashCode} kết hợp chuỗi đặc trưng -
     * không phải mã băm mật, chỉ cần đủ để phát hiện trùng lặp payload.
     *
     * @param cmd lệnh cần băm
     * @return chuỗi hex biểu diễn mã băm
     */
    private static String payloadHash(CreateRelationshipCommand cmd) {
        // Kết hợp các trường cốt lõi của lệnh thành một chuỗi để băm.
        String s = cmd.treeId() + "|" + cmd.kind() + "|" + cmd.fromMemberId() + "|" + cmd.toMemberId();
        return Integer.toHexString(s.hashCode());
    }

    /**
     * Interface trừu tượng cho đồng hồ - cho phép tiêm {@code Instant::now}
     * trong production và một đồng hồ giả trong test.
     */
    public interface Clock { Instant now(); }
}