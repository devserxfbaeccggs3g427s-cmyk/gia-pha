package com.familya.sharing.application.usecase;

import com.familya.sharing.application.port.in.CreateShareLinkCommand;
import com.familya.sharing.application.port.out.ShareAuthorization;
import com.familya.sharing.application.port.out.ShareChangePublisher;
import com.familya.sharing.application.port.out.ShareLinkRepository;
import com.familya.sharing.domain.model.ShareLink;
import com.familya.platform.error.ForbiddenException;
import com.familya.platform.telemetry.PlatformMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Use case tạo mới một liên kết chia sẻ (share link) cho một cây gia phả.
 * <p>
 * Luồng xử lý chính:
 * <ol>
 *     <li>Ghi nhận metric mutation.</li>
 *     <li>Phân quyền dựa trên {@link ShareAuthorization} &mdash; nếu không được
 *         phép thì ném {@link ForbiddenException}.</li>
 *     <li>Sinh token ngẫu nhiên và băm SHA-256 (chỉ lưu hash).</li>
 *     <li>Tạo đối tượng {@link ShareLink} mới với các trường khởi tạo và ghi vào
 *         cơ sở dữ liệu.</li>
 *     <li>Phát hành sự kiện {@code ShareLinkCreated} thông qua outbox.</li>
 * </ol>
 *
 * @author gia-pha platform team
 */
@Service
public class CreateShareLinkUseCase {

    /**
     * Logger dùng để ghi nhận log cho use case.
     */
    private static final Logger LOG = LoggerFactory.getLogger(CreateShareLinkUseCase.class);

    /**
     * Nguồn sinh số ngẫu nhiên an toàn (cryptographically secure) dùng để
     * sinh token chia sẻ.
     */
    private static final SecureRandom RNG = new SecureRandom();

    private final ShareLinkRepository repo;
    private final ShareAuthorization authz;
    private final ShareChangePublisher publisher;
    private final PlatformMetrics metrics;

    /**
     * Khởi tạo use case với các phụ thuộc cần thiết.
     *
     * @param repo      kho lưu trữ liên kết chia sẻ.
     * @param authz     cổng phân quyền.
     * @param publisher cổng phát hành sự kiện thay đổi.
     * @param metrics   bộ thu thập metric của platform.
     */
    public CreateShareLinkUseCase(ShareLinkRepository repo, ShareAuthorization authz,
                                  ShareChangePublisher publisher, PlatformMetrics metrics) {
        this.repo = repo;
        this.authz = authz;
        this.publisher = publisher;
        this.metrics = metrics;
    }

    /**
     * Thực thi việc tạo liên kết chia sẻ.
     *
     * @param cmd lệnh tạo liên kết {@link CreateShareLinkCommand}.
     * @return {@link Result} chứa định danh liên kết, token thô và thời điểm hết hạn.
     * @throws ForbiddenException nếu người dùng không có quyền tạo liên kết trên cây.
     */
    @Transactional
    public Result execute(CreateShareLinkCommand cmd) {
        // Bước 1: Báo hiệu metric rằng mutation được chấp nhận.
        metrics.mutationAccepted("sharing-service", "createShareLink");

        // Bước 2: Phân quyền dựa trên cây + người dùng + phiên bản kỳ vọng của cây.
        ShareAuthorization.Decision d = authz.authorize(cmd.treeId(), cmd.actingUser(), cmd.expectedTreeRevision());
        if (!d.isAllowed()) {
            // Từ chối với thông điệp lý do rõ ràng để log/giải thích.
            throw new ForbiddenException("Cannot create share link: " + d.reason());
        }

        // Bước 3: Chuyển đổi chuỗi scope/role sang enum; nếu role null thì dùng VIEWER.
        ShareLink.Scope scope = ShareLink.Scope.valueOf(cmd.scope().toUpperCase());
        ShareLink.Role role = cmd.role() == null ? ShareLink.Role.VIEWER : ShareLink.Role.valueOf(cmd.role().toUpperCase());

        // Bước 4: Sinh token ngẫu nhiên 32 byte, hex hóa &mdash; chỉ lưu hash để bảo mật.
        String token = generateToken();
        String tokenHash = sha256Hex(token);

        // Bước 5: Tạo đối tượng ShareLink với các trường khởi tạo (revokedAt = null, version = 0).
        Instant now = Instant.now();
        ShareLink link = new ShareLink(
                UUID.randomUUID(), cmd.treeId(), scope, cmd.targetId(), role,
                tokenHash, cmd.actingUser(), now, cmd.expiresAt(), null, null, 0L, 0L);

        // Bước 6: Ghi vào DB trong transaction hiện tại.
        repo.insert(link);

        // Bước 7: Phát hành sự kiện ShareLinkCreated qua outbox (cùng transaction).
        publisher.shareLinkCreated(link);

        LOG.info("Created share link id={} tree={} scope={} role={}", link.id(), cmd.treeId(), scope, role);

        // Trả về kết quả &mdash; lưu ý: token thô chỉ trả về MỘT LẦN ở đây.
        return new Result(link.id(), token, link.expiresAt());
    }

    /**
     * Sinh một token chia sẻ ngẫu nhiên 32 byte được hex hóa (64 ký tự).
     * Sử dụng {@link SecureRandom} để đảm bảo tính không thể đoán.
     *
     * @return chuỗi hex 64 ký tự đại diện cho token.
     */
    public static String generateToken() {
        byte[] bytes = new byte[32];
        RNG.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    /**
     * Băm SHA-256 của chuỗi đầu vào và trả về kết quả dưới dạng hex.
     *
     * @param s chuỗi đầu vào (thường là token thô).
     * @return chuỗi hex 64 ký tự đại diện cho giá trị băm.
     */
    public static String sha256Hex(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(s.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            // Lỗi này chỉ xảy ra khi môi trường thiếu thuật toán SHA-256 &mdash;
            // gần như không thể xảy ra trong JVM tiêu chuẩn, đóng gói lại.
            throw new IllegalStateException(e);
        }
    }

    /**
     * Kết quả trả về của {@link #execute(CreateShareLinkCommand)}.
     *
     * @param shareId   định danh của liên kết vừa tạo.
     * @param token     token thô (chỉ trả về đúng một lần cho client).
     * @param expiresAt thời điểm hết hạn (có thể {@code null} nếu không giới hạn).
     */
    public record Result(UUID shareId, String token, Instant expiresAt) { }
}