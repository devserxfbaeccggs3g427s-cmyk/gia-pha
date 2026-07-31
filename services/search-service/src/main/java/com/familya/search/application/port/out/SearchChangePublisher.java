package com.familya.search.application.port.out;

/**
 * Cổng (port) phát sự kiện thay đổi - được giữ cố ý là không-op.
 *
 * <p>Service search chỉ chứa read-model, không phát sinh sự kiện liên
 * service. Interface này tồn tại để tầng application có thể được nối dây
 * đối xứng với các service khác, đồng thời tạo sẵn một "điểm chèn" chuẩn
 * cho công cụ vận hành trong tương lai (ví dụ: cảnh báo rebuild).</p>
 */
public interface SearchChangePublisher {

    /**
     * Thao tác không-op. Triển khai mặc định chỉ ghi log trace, không phát
     * bất kỳ sự kiện nào ra ngoài service.
     */
    void noop();
}
