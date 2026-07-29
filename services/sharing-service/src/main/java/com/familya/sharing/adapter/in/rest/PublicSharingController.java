package com.familya.sharing.adapter.in.rest;

import com.familya.sharing.application.port.in.PublicLookupQuery;
import com.familya.sharing.application.usecase.ResolvePublicProjectionUseCase;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v2/public/sharing")
public class PublicSharingController {

    private final ResolvePublicProjectionUseCase lookup;

    public PublicSharingController(ResolvePublicProjectionUseCase lookup) {
        this.lookup = lookup;
    }

    @GetMapping("/{token}/media/{mediaId}")
    public ResponseEntity<Map<String, Object>> media(@PathVariable String token, @PathVariable UUID mediaId) {
        var result = lookup.execute(new PublicLookupQuery(token, mediaId, Instant.now()));
        return ResponseEntity.ok(result.projection());
    }

    @GetMapping("/{token}")
    public ResponseEntity<Map<String, Object>> scope(@PathVariable String token) {
        var result = lookup.execute(new PublicLookupQuery(token, null, Instant.now()));
        return ResponseEntity.ok(result.projection());
    }
}
