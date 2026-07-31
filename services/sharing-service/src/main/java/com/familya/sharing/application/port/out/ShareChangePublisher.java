package com.familya.sharing.application.port.out;

import com.familya.sharing.domain.model.ShareLink;
import com.familya.platform.outbox.OutboxRecord;

import java.util.List;
import java.util.UUID;

/**
 * Cổng (port) phát hành các sự kiện thay đổi liên quan đến share link ra bên
 * ngoài thông qua mô hình <b>Transactional Outbox</b>.
 * <p>
 * Mọi thay đổi (tạo, thu hồi, tái tạo projection) đều được ghi vào bảng
 * outbox trong cùng transaction với thay đổi dữ liệu, đảm bảo tính nhất quán
 * giữa trạng thái hệ thống và sự kiện phát ra. Cổng này được
 * {@code OutboxShareChangePublisher} hiện thực.
 */
public interface ShareChangePublisher {

    /**
     * Ghi một bản ghi outbox "thô" (đã được tạo sẵn).
     *
     * @param record bản ghi outbox cần ghi vào hàng đợi phát hành.
     */
    void stage(OutboxRecord record);

    /**
     * Phát hành sự kiện {@code ShareLinkCreated} cho một liên kết mới được tạo.
     *
     * @param link liên kết chia sẻ vừa được tạo.
     */
    void shareLinkCreated(ShareLink link);

    /**
     * Phát hành sự kiện {@code ShareLinkRevoked} cho một liên kết vừa bị thu hồi.
     *
     * @param link liên kết chia sẻ vừa bị thu hồi.
     */
    void shareLinkRevoked(ShareLink link);

    /**
     * Phát hành sự kiện {@code ShareProjectionRebuilt} khi một projection
     * công khai được tái tạo.
     *
     * @param treeId   định danh cây gia phả.
     * @param watermark phiên bản watermark mới.
     */
    void projectionRebuilt(UUID treeId, long watermark);

    /**
     * Lấy danh sách các bản ghi outbox đang chờ xuất bản (chưa có
     * {@code publishedAt}), sắp xếp theo thời điểm phát sinh tăng dần.
     *
     * @param limit số bản ghi tối đa cần lấy.
     * @return danh sách các {@link OutboxRecord} đang chờ.
     */
    List<OutboxRecord> listPending(int limit);

    /**
     * Đánh dấu một bản ghi outbox là đã được xuất bản thành công lên Kafka.
     *
     * @param id định danh của bản ghi outbox.
     */
    void markPublished(UUID id);
}