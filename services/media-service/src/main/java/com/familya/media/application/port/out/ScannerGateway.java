package com.familya.media.application.port.out;

import com.familya.media.domain.model.ScannerResult;

import java.util.UUID;

/**
 * Pluggable anti-virus / malware scanner. Implementations must
 * return {@link com.familya.media.domain.model.ScannerResult.Outcome#FAILED}
 * on any outage — never {@code CLEAN} — to keep the gate fail-closed.
 *
 * <p>Port ra (driven port) của kiến trúc hexagonal: cổng tích hợp với
 * dịch vụ anti-virus / malware scanner bên ngoài (ClamAV, VirusTotal,
 * ...).</p>
 *
 * <p><b>Quy tắc fail-closed:</b> mọi lỗi hạ tầng (timeout, connection
 * refused, ...) phải trả {@code FAILED} chứ không được {@code CLEAN},
 * để media không vô tình được promote khi scanner không hoạt động.</p>
 */
public interface ScannerGateway {

    /**
     * Quét một blob đang ở đường dẫn quarantine.
     *
     * @param mediaId        UUID media cần quét (chỉ để audit / logging).
     * @param quarantinePath exact-path tới blob trong store; scanner sẽ
     *                       đọc trực tiếp từ đây.
     * @param sha256         SHA-256 hash kỳ vọng; scanner có thể dùng để
     *                       tối ưu cache lookup.
     * @param mimeType       MIME type; scanner có thể dùng để chọn
     *                       engine phù hợp.
     * @param byteSize       kích thước bytes; scanner có thể dùng để
     *                       giới hạn / chunk.
     * @return {@link ScannerResult} mô tả kết quả quét. <b>Phải</b> là
     *         {@code CLEAN} / {@code INFECTED} / {@code FAILED}. Không
     *         bao giờ trả {@code CLEAN} khi scanner không hoạt động.
     */
    ScannerResult scan(UUID mediaId, String quarantinePath, String sha256, String mimeType,
                       long byteSize);
}
