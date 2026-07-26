package vn.giapha.research.identity.adapter.in.web;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import vn.giapha.research.identity.application.bridge.BridgeKeyRegistry;
import vn.giapha.research.identity.application.bridge.BridgeReplayStore;
import vn.giapha.research.identity.application.bridge.BridgeTokenService;

/**
 * Operator-facing bridge controls (Task 18.4). Lives behind
 * {@code @PreAuthorize("hasAuthority('OPS')")} so only operators on the
 * dedicated management surface can toggle the kill switch or query the
 * active key set — these endpoints must never be reachable to ordinary
 * authenticated users.
 */
@RestController
@RequestMapping("/api/internal/bridge")
public class BridgeOperatorController {

    private final BridgeTokenService bridge;
    private final BridgeKeyRegistry keys;
    private final BridgeReplayStore replayStore;

    public BridgeOperatorController(BridgeTokenService bridge,
            BridgeKeyRegistry keys, BridgeReplayStore replayStore) {
        this.bridge = bridge;
        this.keys = keys;
        this.replayStore = replayStore;
    }

    @GetMapping
    public Status status() {
        return new Status(bridge.isKilled(),
                bridge.activeSigningKeyId(),
                keys.verificationKeys().stream().map(k -> k.keyId()).sorted().toList(),
                replayStore.size(),
                bridge.maxLifetime().getSeconds());
    }

    @PostMapping("/kill-switch")
    @PreAuthorize("hasAuthority('OPS')")
    public Status killSwitch(@RequestParam(name = "enabled", defaultValue = "true") boolean enabled) {
        bridge.killSwitch(enabled);
        return status();
    }

    public record Status(
            boolean killSwitch,
            String activeSigningKeyId,
            java.util.List<String> verificationKeyIds,
            int pendingReplayCount,
            long maxLifetimeSeconds) {}
}
