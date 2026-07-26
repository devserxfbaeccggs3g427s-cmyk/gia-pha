package vn.giapha.research.binarystorage.adapter.in.web;

import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import vn.giapha.research.binarystorage.adapter.out.gateway.GatewayNotificationVerifier;
import vn.giapha.research.binarystorage.application.port.in.CompleteUploadUseCase;
import vn.giapha.research.binarystorage.shared.error.UnauthorizedException;
import vn.giapha.research.binarystorage.shared.web.ApiSuccess;

/**
 * Receives forwarded upload-completed events from the blob control gateway
 * (Task 13.4 → Task 15.3). The gateway signs each notification with its
 * notify keys over the exact request path and byte-exact body; anything that
 * fails verification is rejected with 401 before any state is touched.
 *
 * <p>Responses drive the retry loop end-to-end: a 2xx acknowledges the event,
 * any other status makes the gateway fail its own callback so Vercel Blob
 * redelivers. Terminal rejections (validation, malware) still return 2xx —
 * the intent is already settled as {@code FAILED} and redelivery would be a
 * pointless no-op; only infrastructure failures propagate as 5xx.
 */
@RestController
class UploadCompletionController {

    private static final Logger log = LoggerFactory.getLogger(UploadCompletionController.class);

    private final GatewayNotificationVerifier verifier;
    private final CompleteUploadUseCase completeUpload;
    private final JsonMapper jsonMapper;

    UploadCompletionController(GatewayNotificationVerifier verifier,
            CompleteUploadUseCase completeUpload, JsonMapper jsonMapper) {
        this.verifier = verifier;
        this.completeUpload = completeUpload;
        this.jsonMapper = jsonMapper;
    }

    @PostMapping(path = "/api/internal/blob/upload-completions",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    ApiSuccess<Map<String, Boolean>> onUploadCompleted(
            HttpServletRequest request,
            // Raw body: verification is byte-exact over what the gateway signed.
            @RequestBody String body,
            @RequestHeader(name = "x-giapha-timestamp", required = false) String timestamp,
            @RequestHeader(name = "x-giapha-nonce", required = false) String nonce,
            @RequestHeader(name = "x-giapha-issuer", required = false) String issuer,
            @RequestHeader(name = "x-giapha-signature", required = false) String signature) {
        // The gateway signs over the URL pathname (service-auth canonical string).
        if (!verifier.verify("POST", request.getRequestURI(), body,
                timestamp, nonce, issuer, signature)) {
            throw new UnauthorizedException("Invalid notification signature");
        }
        JsonNode payload = jsonMapper.readTree(body);
        JsonNode tokenPayload = payload.path("tokenPayload");
        if (!tokenPayload.isString() || tokenPayload.asString().isBlank()) {
            // No correlation id: nothing to drive. Acknowledge — the orphan
            // sweep reaps whatever landed without an intent (Task 15.7).
            log.warn("Upload completion without tokenPayload; acknowledged as no-op");
            return ApiSuccess.of(Map.of("received", true));
        }
        completeUpload.handleCompletion(tokenPayload.asString());
        return ApiSuccess.of(Map.of("received", true));
    }
}
