package vn.giapha.research.transfer.application.service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import vn.giapha.research.transfer.support.NotFoundException;
import vn.giapha.research.transfer.domain.model.ImportFormat;

/**
 * Parser registry (Task 29.1). New formats plug in via Spring auto-wiring:
 * any {@link ImportParser} bean contributes its {@link ImportFormat} to the
 * map so {@link ImportService#preview}/{@link ImportService#execute} can
 * resolve the right implementation.
 */
@Component
public class ImportParserRegistry {

    private final Map<ImportFormat, ImportParser> byFormat = new EnumMap<>(ImportFormat.class);

    public ImportParserRegistry(List<ImportParser> parsers) {
        for (ImportParser parser : parsers) {
            byFormat.put(parser.format(), parser);
        }
    }

    public ImportParser require(ImportFormat format) {
        ImportParser parser = byFormat.get(format);
        if (parser == null) {
            throw new NotFoundException("IMPORT_PARSER_NOT_FOUND",
                    "No parser registered for " + format);
        }
        return parser;
    }

    public List<ImportFormat> availableFormats() {
        return List.copyOf(byFormat.keySet());
    }
}
