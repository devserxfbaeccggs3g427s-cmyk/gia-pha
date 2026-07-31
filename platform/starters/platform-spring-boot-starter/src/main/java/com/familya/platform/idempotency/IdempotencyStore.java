package com.familya.platform.idempotency;

import com.familya.platform.api.AsyncOperation;

import java.util.Optional;
import java.util.UUID;

/**
 * Cổng (port) lưu trữ bản ghi idempotency cho các thao tác bất đồng bộ.
 *
 * <p>Gateway ghi lại cặp {@code (key, payload-hash, operation)} và phát lại
 * phản hồi đã ghi nhận khi có yêu cầu retry. Mỗi dịch vụ thực hiện một thao
 * tác đột biến xuyên dịch vụ sẽ triển khai port này dựa trên kho lưu trữ
 * idempotency riêng của mình:</p>
 * <ul>
 *   <li><b>MySQL</b> là baseline được khuyến nghị.</li>
 *   <li><b>Redis</b> chỉ được phép khi đảm bảo tính bền vững (persistence)
 *       đã được tài liệu hoá và phê duyệt.</li>
 * </ul>
 *
 * @author Family Tree Platform Team
 */
public interface IdempotencyStore {

    /**
     * Tìm kiếm thao tác đã được ghi nhận trước đó với key cho trước.
     *
     * @param key idempotency key do client cung cấp
     * @return {@link Optional} chứa envelope {@link AsyncOperation} nếu đã được
     *         ghi nhận trước đó, {@link Optional#empty()} nếu chưa có
     */
    Optional<AsyncOperation> find(String key);

    /**
     * Ghi nhận một thao tác dưới một idempotency key kèm payload hash.
     *
     * <p>Quy tắc bắt buộc đối với mọi triển khai:</p>
     * <ul>
     *   <li>Nếu key chưa tồn tại: ghi nhận mới.</li>
     *   <li>Nếu key đã tồn tại với cùng payload hash: phải idempotent (không lỗi).</li>
     *   <li>Nếu key đã tồn tại với payload hash khác: PHẢI ném
     *       {@link com.familya.platform.error.IdempotencyConflictException}.</li>
     * </ul>
     *
     * @param key         idempotency key do client cung cấp
     * @param payloadHash hash của payload (thường là SHA-256) để phát hiện sai lệch
     * @param operation   envelope thao tác cần ghi nhận
     * @throws com.familya.platform.error.IdempotencyConflictException khi key được
     *         tái sử dụng với payload hash khác
     */
    void record(String key, String payloadHash, AsyncOperation operation);
}
