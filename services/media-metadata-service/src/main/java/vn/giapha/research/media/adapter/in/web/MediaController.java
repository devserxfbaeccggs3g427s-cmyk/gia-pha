package vn.giapha.research.media.adapter.in.web;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import vn.giapha.research.media.application.service.MediaService;
import vn.giapha.research.media.application.service.MediaService.MediaFilter;
import vn.giapha.research.media.domain.Album;
import vn.giapha.research.media.domain.MediaObject;
import vn.giapha.research.media.shared.error.UnauthorizedException;
import vn.giapha.research.media.shared.principal.Principal;
import vn.giapha.research.media.shared.web.ApiSuccess;

/**
 * Media + album CRUD controller. Mirrors the legacy BFF surface
 * ({@code /api/trees/{treeId}/albums} and
 * {@code /api/trees/{treeId}/media}); the BFF forwards via the
 * gateway.
 */
@RestController
@RequestMapping(path = "/api/trees/{treeExternalId}", produces = "application/json")
public class MediaController {

    private final MediaService media;

    public MediaController(MediaService media) {
        this.media = media;
    }

    @GetMapping("/albums")
    ApiSuccess<List<Map<String, Object>>> albums(
            @PathVariable String treeExternalId,
            Authentication auth) {
        List<Map<String, Object>> rows = media.listAlbums(principal(auth), treeExternalId).stream()
                .map(this::toMapAlbum).toList();
        return ApiSuccess.ok(rows);
    }

    @GetMapping("/media")
    ApiSuccess<List<Map<String, Object>>> listMedia(
            @PathVariable String treeExternalId,
            Authentication auth,
            @RequestParam(required = false) String memberId,
            @RequestParam(required = false) String eventId,
            @RequestParam(required = false) String albumId) {
        MediaFilter filter = buildFilter(memberId, eventId, albumId);
        List<Map<String, Object>> rows = media
                .list(principal(auth), treeExternalId, filter)
                .stream().map(this::toMapMedia).toList();
        return ApiSuccess.ok(rows);
    }

    @GetMapping("/media/{externalId}")
    ApiSuccess<Map<String, Object>> detailMedia(
            @PathVariable String treeExternalId,
            @PathVariable String externalId,
            Authentication auth) {
        return ApiSuccess.ok(toMapMedia(
                media.detail(principal(auth), treeExternalId, externalId)));
    }

    @DeleteMapping("/media/{externalId}")
    ApiSuccess<Void> deleteMedia(
            @PathVariable String treeExternalId,
            @PathVariable String externalId,
            Authentication auth) {
        media.delete(principal(auth), treeExternalId, externalId, Instant.now());
        return ApiSuccess.ok(null);
    }

    @DeleteMapping("/albums/{albumExternalId}")
    ApiSuccess<Void> deleteAlbum(
            @PathVariable String treeExternalId,
            @PathVariable String albumExternalId,
            Authentication auth) {
        media.deleteAlbum(principal(auth), treeExternalId, albumExternalId, Instant.now());
        return ApiSuccess.ok(null);
    }

    @PostMapping(path = "/upload", produces = "application/json")
    ApiSuccess<Map<String, Object>> requestUploadIntent(
            @PathVariable String treeExternalId,
            @RequestBody UploadIntent body,
            Authentication auth) {
        Map<String, Object> intent = Map.of(
                "uploadId", "ui-" + java.util.UUID.randomUUID(),
                "uploadUrl", "https://blob-storage.local/upload?token=" + java.util.UUID.randomUUID(),
                "expiresAt", Instant.now().plusSeconds(60 * 60 * 24).toString()
        );
        return ApiSuccess.ok(intent);
    }

    private Map<String, Object> toMapAlbum(Album a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", a.externalId());
        m.put("treeId", String.valueOf(a.treeKey()));
        m.put("title", a.title());
        m.put("description", a.description());
        m.put("mediaIds", List.of());
        m.put("createdAt", a.createdAt() == null ? null : a.createdAt().toString());
        m.put("updatedAt", a.updatedAt() == null ? null : a.updatedAt().toString());
        return m;
    }

    private Map<String, Object> toMapMedia(MediaObject o) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", o.externalId());
        m.put("treeId", String.valueOf(o.treeKey()));
        m.put("filename", o.filename());
        m.put("originalName", o.originalName());
        m.put("mimeType", o.mimeType());
        m.put("fileSize", o.fileSize());
        m.put("status", o.status() == null ? "PENDING" : o.status().name());
        m.put("uploadedAt", o.uploadedAt() == null ? null : o.uploadedAt().toString());
        m.put("blobUrl", o.originalObjectPath());
        m.put("thumbnailUrl", o.thumbnailObjectPath());
        return m;
    }

    private static MediaFilter buildFilter(String memberId, String eventId, String albumId) {
        Long memberKey = memberId == null ? null : (long) memberId.hashCode();
        Long albumKey = albumId == null ? null : (long) albumId.hashCode();
        return new MediaFilter(null, albumKey, memberKey);
    }

    private static Principal principal(Authentication auth) {
        if (auth == null || auth.getName() == null) {
            throw new UnauthorizedException("UNAUTHORIZED", "Authentication required");
        }
        return new Principal(auth.getName(), auth.getName() + "@giapha.local", "Authenticated user");
    }

    public record UploadIntent(
            String filename,
            String mimeType,
            long size,
            String memberId,
            String eventId,
            String albumId) {}
}
