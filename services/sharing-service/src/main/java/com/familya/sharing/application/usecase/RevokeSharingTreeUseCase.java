package com.familya.sharing.application.usecase;

import com.familya.sharing.application.port.in.RevokeSharingTreeCommand;
import com.familya.sharing.application.port.out.ShareLinkRepository;
import com.familya.sharing.domain.model.ShareLink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Use case thu hồi tất cả các liên kết chia sẻ thuộc về một cây gia phả.
 * <p>
 * Được sử dụng như một bước trong <b>saga xóa cây</b>: khi cây bị xóa, mọi
 * liên kết chia sẻ liên quan phải được đánh dấu thu hồi để tránh rò rỉ dữ liệu.
 * Lý do thu hồi được gắn tiền tố {@code "delete-tree-saga:<operationId>"} để
 * sau này có thể nhận diện và khôi phục trong bước bù trừ của saga.
 *
 * @author gia-pha platform team
 */
@Service
public class RevokeSharingTreeUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(RevokeSharingTreeUseCase.class);

    private final ShareLinkRepository repo;

    /**
     * Khởi tạo use case.
     *
     * @param repo kho lưu trữ liên kết chia sẻ.
     */
    public RevokeSharingTreeUseCase(ShareLinkRepository repo) {
        this.repo = repo;
    }

    /**
     * Thực thi thu hồi hàng loạt.
     *
     * @param cmd lệnh {@link RevokeSharingTreeCommand}.
     * @return {@link Result} chứa số bản ghi bị ảnh hưởng và các phiên bản đã áp dụng.
     */
    @Transactional
    public Result execute(RevokeSharingTreeCommand cmd) {
        // Bước 1: Lấy tất cả liên kết của cây.
        List<ShareLink> links = repo.listByTree(cmd.treeId());

        Instant now = Instant.now();
        long maxVersion = 0L;

        // Bước 2: Lặp qua từng liên kết, thu hồi những cái chưa thu hồi.
        for (ShareLink l : links) {
            if (l.revokedAt() != null) continue;
            ShareLink revoked = new ShareLink(l.id(), l.treeId(), l.scope(), l.targetId(),
                    l.role(), l.tokenHash(), l.createdByUserId(), l.createdAt(), l.expiresAt(),
                    now, "delete-tree-saga:" + cmd.operationId(), l.revision(), l.version() + 1);
            repo.update(revoked);
            maxVersion = Math.max(maxVersion, revoked.version());
        }

        // Bước 3: Đảm bảo phiên bản trả về đáp ứng yêu cầu tối thiểu của saga.
        long appliedVersion = Math.max(maxVersion, cmd.targetAggregateVersion());

        LOG.info("Revoked {} share links on tree {} operationId={}",
                links.size(), cmd.treeId(), cmd.operationId());

        // Trả về kết quả &mdash; chú ý affectedCount = tổng số liên kết (không chỉ số vừa thu hồi).
        return new Result(links.size(), appliedVersion, cmd.targetEpoch());
    }

    /**
     * Kết quả thu hồi.
     *
     * @param affectedCount         tổng số liên kết đã xét trên cây.
     * @param appliedAggregateVersion phiên bản aggregate đã được áp dụng.
     * @param appliedEpoch          epoch đã được áp dụng.
     */
    public record Result(int affectedCount, long appliedAggregateVersion, long appliedEpoch) { }
}