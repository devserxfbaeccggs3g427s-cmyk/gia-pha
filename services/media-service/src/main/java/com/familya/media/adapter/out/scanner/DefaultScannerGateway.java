package com.familya.media.adapter.out.scanner;

import com.familya.media.application.port.out.ScannerGateway;
import com.familya.media.domain.model.ScannerResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Default scanner gateway. Pluggable: the {@code failClosed} switch
 * returns {@code FAILED} on unavailability. Replace with the
 * institutional AV vendor adapter once integrated; the failure shape
 * here is the canonical contract.
 */
@Component
public class DefaultScannerGateway implements ScannerGateway {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultScannerGateway.class);

    private final boolean failClosed;
    private final String vendorTag;

    public DefaultScannerGateway(@Value("${familya.scanner.fail-closed:true}") boolean failClosed,
                                  @Value("${familya.scanner.vendor:placeholder}") String vendorTag) {
        this.failClosed = failClosed;
        this.vendorTag = vendorTag;
    }

    @Override
    public ScannerResult scan(UUID mediaId, String quarantinePath, String sha256,
                              String mimeType, long byteSize) {
        LOG.info("Scanner invoked mediaId={} vendor={} size={}", mediaId, vendorTag, byteSize);
        if (failClosed && vendorTag.equals("placeholder")) {
            return new ScannerResult(ScannerResult.Outcome.FAILED, "scanner-not-configured");
        }
        // Real implementation would call the AV vendor; placeholder returns CLEAN.
        return new ScannerResult(ScannerResult.Outcome.CLEAN, "sha256:" + sha256);
    }
}
