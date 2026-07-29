package com.familya.media.application.port.out;

import com.familya.media.domain.model.ScannerResult;

import java.util.UUID;

/**
 * Pluggable anti-virus / malware scanner. Implementations must
 * return {@link com.familya.media.domain.model.ScannerResult.Outcome#FAILED}
 * on any outage — never {@code CLEAN} — to keep the gate fail-closed.
 */
public interface ScannerGateway {

    ScannerResult scan(UUID mediaId, String quarantinePath, String sha256, String mimeType,
                       long byteSize);
}
