package com.familya.media.adapter.out.scanner;

import com.familya.media.application.port.out.ScannerGateway;
import com.familya.media.domain.model.ScannerResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Adapter đầu ra (outbound) — cổng scanner mặc định.
 * <p>
 * Pluggable: khi vendor chưa cấu hình ({@code vendorTag=placeholder}) và
 * {@code failClosed=true} (mặc định), trả {@code FAILED} để hệ thống
 * fail-closed thay vì vô tình "CLEAN" các tệp chưa được kiểm tra. Khi vendor
 * đã được tích hợp, thay bằng adapter AV cụ thể; failure shape ở đây là
 * hợp đồng chuẩn.
 */
@Component
public class DefaultScannerGateway implements ScannerGateway {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultScannerGateway.class);

    private final boolean failClosed;
    private final String vendorTag;

    /**
     * Khởi tạo gateway.
     *
     * @param failClosed nếu true, trả FAILED khi vendor chưa cấu hình (mặc định true).
     * @param vendorTag  tên vendor (placeholder nếu chưa tích hợp).
     */
    public DefaultScannerGateway(@Value("${familya.scanner.fail-closed:true}") boolean failClosed,
                                  @Value("${familya.scanner.vendor:placeholder}") String vendorTag) {
        this.failClosed = failClosed;
        this.vendorTag = vendorTag;
    }

    /**
     * Thực hiện quét virus.
     * <p>
     * Quyết định:
     * <ul>
     *   <li>Nếu fail-closed và vendor=placeholder → trả {@code FAILED} "scanner-not-configured".</li>
     *   <li>Ngược lại (vendor thật hoặc failClosed=false) → placeholder trả {@code CLEAN}.</li>
     * </ul>
     *
     * @param mediaId        UUID media.
     * @param quarantinePath đường dẫn trong vùng cách ly.
     * @param sha256         hash của tệp.
     * @param mimeType       MIME type.
     * @param byteSize       kích thước.
     * @return {@link ScannerResult}.
     */
    @Override
    public ScannerResult scan(UUID mediaId, String quarantinePath, String sha256,
                              String mimeType, long byteSize) {
        LOG.info("Scanner invoked mediaId={} vendor={} size={}", mediaId, vendorTag, byteSize);
        // Fail-closed: vendor placeholder + failClosed → trả FAILED để không promote "CLEAN" giả.
        if (failClosed && vendorTag.equals("placeholder")) {
            return new ScannerResult(ScannerResult.Outcome.FAILED, "scanner-not-configured");
        }
        // Real implementation would call the AV vendor; placeholder returns CLEAN.
        return new ScannerResult(ScannerResult.Outcome.CLEAN, "sha256:" + sha256);
    }
}
