package vn.giapha.research.transfer.adapter.in.web;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import vn.giapha.research.transfer.support.ApiSuccess;
import vn.giapha.research.transfer.support.Principal;
import vn.giapha.research.transfer.support.UnauthorizedException;
import vn.giapha.research.transfer.application.service.ImportService;
import vn.giapha.research.transfer.domain.model.ImportDuplicateStrategy;
import vn.giapha.research.transfer.domain.model.ImportFormat;
import vn.giapha.research.transfer.domain.model.ImportJob;

@RestController
@RequestMapping(path = "/api/trees/{treeExternalId}/imports",
        produces = "application/json")
public class ImportController {

    private final ImportService imports;

    public ImportController(ImportService imports) {
        this.imports = imports;
    }

    @PostMapping(path = "/preview", consumes = "application/octet-stream")
    ApiSuccess<Map<String, Object>> preview(@org.springframework.web.bind.annotation.PathVariable
            String treeExternalId,
            @RequestParam("format") ImportFormat format,
            @RequestParam(value = "strategy", defaultValue = "SKIP")
            ImportDuplicateStrategy strategy,
            HttpServletRequest request, Authentication auth) throws IOException {
        byte[] payload = request.getInputStream().readAllBytes();
        ImportJob job = imports.preview(principal(auth), treeExternalId, format, payload,
                strategy);
        return ApiSuccess.ok(toMap(job));
    }

    @PostMapping(path = "/execute", consumes = "application/octet-stream")
    ApiSuccess<Map<String, Object>> execute(
            @org.springframework.web.bind.annotation.PathVariable String treeExternalId,
            @RequestParam("format") ImportFormat format,
            @RequestParam(value = "strategy", defaultValue = "SKIP")
            ImportDuplicateStrategy strategy,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest request, Authentication auth) throws IOException {
        byte[] payload = request.getInputStream().readAllBytes();
        ImportJob job = imports.execute(principal(auth), treeExternalId, format, payload,
                strategy, idempotencyKey);
        return ApiSuccess.ok(toMap(job));
    }

    private Map<String, Object> toMap(ImportJob job) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", job.externalId());
        map.put("treeExternalId", job.treeExternalId());
        map.put("format", job.inputFormat().name());
        map.put("strategy", job.duplicateStrategy().name());
        map.put("status", job.status().name());
        map.put("totalCount", job.totalCount());
        map.put("acceptedCount", job.acceptedCount());
        map.put("skippedCount", job.skippedCount());
        map.put("errorCount", job.errorCount());
        map.put("errors", job.errors());
        map.put("completedAt", job.completedAt() == null ? null
                : job.completedAt().toString());
        return map;
    }

    private static Principal principal(Authentication auth) {
        if (auth == null || auth.getName() == null) {
            throw new UnauthorizedException("UNAUTHORIZED", "Authentication required");
        }
        return new Principal(auth.getName(),
                auth.getName() + "@giapha.local", "Authenticated user");
    }
}
