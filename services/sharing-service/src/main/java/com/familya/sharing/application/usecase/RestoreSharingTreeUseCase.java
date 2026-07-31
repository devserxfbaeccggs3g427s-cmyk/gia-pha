package com.familya.sharing.application.usecase;

import com.familya.sharing.application.port.in.RestoreSharingTreeCommand;
import com.familya.sharing.application.port.out.ShareLinkRepository;
import com.familya.sharing.domain.model.ShareLink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use case khôi phục các liên kết chia sẻ trên một cây đã bị thu hồi bởi saga
 * xóa cây (compensation step).
 * <p>
 * Chỉ các liên kết có {@code revocationReason} bắt đầu bằng
 * {@code "delete-tree-saga:"} mới được khôi phục &mdash; đảm bảo không vô tình
 * khôi phục các liên kết đã bị thu hồi vì lý do khác (ví dụ: do chính chủ sở hữu
 * hoặc do vi phạm chính sách).
 *
 * @author gia-pha platform team
 */
@Service
public class RestoreSharingTreeUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(RestoreSharingTreeUseCase.class);

    private final ShareLinkRepository repo;

    /**
     * Khởi tạo use case.
     *
     * @param repo kho lưu trữ liên kết chia sẻ.
     */
    public RestoreSharingTreeUseCase(ShareLinkRepository repo) {
        this.repo = repo;
    }

    /**
     * Thực thi khôi phục.
     *
     * @param cmd lệnh khôi phục {@link RestoreSharingTreeCommand}.
     * @return {@link Result} chứa số liên kết đã khôi phục, phiên bản áp dụng và epoch.
     */
    @Transactional
    public Result execute(RestoreSharingTreeCommand cmd) {
        long maxVersion = 0L;
        int restored = 0;

        // Bước 1: Lặp qua tất cả liên kết của cây.
        for (ShareLink l : repo.listByTree(cmd.treeId())) {
            // Bước 2: Bỏ qua liên kết chưa bị thu hồi.
            if (l.revokedAt() == null) continue;

            // Bước 3: Bỏ qua liên kết không phải do saga xóa cây đánh dấu.
            if (l.revocationReason() == null
                    || !l.revocationReason().startsWith("delete-tree-saga:")) continue;

            // Bước 4: Tạo bản sao đã khôi phục: xoá revokedAt & revocationReason, tăng version.
            ShareLink alive = new ShareLink(l.id(), l.treeId(), l.scope(), l.targetId(),
                    l.role(), l.tokenHash(), l.createdByUserId(),
                    l.createdAt(), l.expiresAt(), null, null,
                    l.revision(), l.version() + 1);
            repo.update(alive);

            // Bước 5: Theo dõi phiên bản tối đa & đếm số bản ghi đã khôi phục.
            maxVersion = Math.max(maxVersion, alive.version());
            restored++;
        }

        LOG.info("Restored {} share links on tree {} operationId={}",
                restored, cmd.treeId(), cmd.operationId());

        // Trả về kết quả; epoch hiện không được sử dụng &mdash; đặt 0 để giữ tương thích saga.
        return new Result(restored, maxVersion, 0L);
    }

    /**
     * Kết quả khôi phục.
     *
     * @param affectedCount         số liên kết đã được khôi phục.
     * @param appliedAggregateVersion phiên bản tối đa đã đạt được.
     * @param appliedEpoch          epoch áp dụng (mặc định {@code 0}).
     */
    public record Result(int affectedCount, long appliedAggregateVersion, long appliedEpoch) { }
}