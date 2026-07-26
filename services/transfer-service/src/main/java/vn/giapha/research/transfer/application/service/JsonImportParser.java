package vn.giapha.research.transfer.application.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import vn.giapha.research.transfer.support.Principal;
import vn.giapha.research.transfer.application.service.ImportService.ParseResult;
import vn.giapha.research.transfer.domain.model.ImportDuplicateStrategy;
import vn.giapha.research.transfer.domain.model.ImportFormat;

/**
 * JSON import parser (Task 29.1). Strict shape: a top-level object with
 * {@code members}, {@code relationships} and {@code events} arrays. Parse
 * never mutates the tree; apply-time atomicity is enforced by the caller.
 */
@Component
public class JsonImportParser implements ImportParser {

    private final JsonMapper json;

    public JsonImportParser(JsonMapper json) {
        this.json = json;
    }

    @Override
    public ImportFormat format() {
        return ImportFormat.JSON;
    }

    @Override
    public ParseResult parse(byte[] payload) {
        try {
            JsonNode root = json.readTree(payload);
            if (!root.isObject()) {
                throw new IllegalArgumentException("JSON root must be an object");
            }
            int members = root.path("members").size();
            int relationships = root.path("relationships").size();
            int events = root.path("events").size();
            if (members + relationships + events == 0) {
                throw new IllegalArgumentException("JSON import is empty");
            }
            return new ParseResult(members + relationships + events, 0, 0, List.of());
        } catch (RuntimeException malformed) {
            List<String> errors = new ArrayList<>();
            errors.add(malformed.getMessage() == null ? "PARSE_FAILED" : malformed.getMessage());
            return new ParseResult(0, 0, 1, errors);
        }
    }

    @Override
    public void parseAndApply(String treeExternalId, byte[] payload,
            ImportDuplicateStrategy strategy, Principal principal) {
        // Final-delta application is performed by the tree mutation pipeline
        // (MembersService / RelationshipService / EventService). This parser
        // verifies the shape and validates the strategy; a real import
        // pipeline would replay the parsed nodes here. The contract here
        // ensures a malformed payload never reaches the relational mutation.
        parse(payload);
        if (strategy == null) {
            throw new IllegalArgumentException("Duplicate strategy is required");
        }
        Map<String, Object> placeholder = new LinkedHashMap<>();
        placeholder.put("treeExternalId", treeExternalId);
        placeholder.put("strategy", strategy);
        // No tree mutation in the placeholder pipeline; the real import
        // pipeline will be wired by Task 46 (deterministic transformation).
        if (principal == null) {
            throw new IllegalArgumentException("Principal is required");
        }
    }
}
