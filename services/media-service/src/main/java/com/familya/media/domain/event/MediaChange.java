package com.familya.media.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Hợp đồng chung cho tất cả các sự kiện thay đổi liên quan đến media trong hệ thống gia phả.
 *
 * <p>Đây là một {@code sealed interface}, chỉ cho phép một tập giới hạn các bản ghi
 * cụ thể triển khai (xem mệnh đề {@code permits}). Nhờ đó, trình biên dịch có thể
 * kiểm tra đầy đủ (exhaustive) tại mọi điểm sử dụng pattern matching, giúp đảm bảo
 * mọi loại sự kiện đều được xử lý khi thêm loại mới.
 *
 * <p>Mỗi sự kiện chứa các trường nhận diện chung (cây gia phả, tài sản media, loại
 * sự kiện, phiên bản sự kiện, số bản sửa đổi) cùng thông tin về topic và khóa
 * phân vùng để hỗ trợ việc phát hành và tiêu thụ sự kiện theo mô hình
 * event-driven (ví dụ: Kafka).
 */
public sealed interface MediaChange
        permits MediaQuarantined, MediaScanned, MediaAssociated, MediaDetached, AlbumCreated, MediaActivated {

    /** @return định danh cây gia phả mà sự kiện thuộc về. */
    UUID treeId();
    /** @return định danh tài sản media (hoặc album) liên quan đến sự kiện. */
    UUID mediaId();
    /** @return tên loại sự kiện, dùng để phân loại khi tiêu thụ. */
    String eventType();
    /** @return phiên bản schema của sự kiện, dùng để tương thích ngược. */
    int eventVersion();
    /** @return số bản sửa đổi của aggregate tại thời điểm phát sự kiện. */
    long revision();
    /** @return thời điểm sự kiện xảy ra. */
    Instant occurredAt();
    /** @return tên topic mà sự kiện được phát lên. */
    String topic();
    /** @return khóa phân vùng, dùng để bảo toàn thứ tự trong cùng một cây gia phả. */
    String partitionKey();
}
