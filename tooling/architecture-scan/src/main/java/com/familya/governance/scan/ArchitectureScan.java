package com.familya.governance.scan;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Repository-wide architecture and contradiction scanner. It enforces the
 * rules in
 * {@code .kiro/specs/spring-boot-backend-migration/governance/task-1-supersede-architecture.md}.
 *
 * <p>The scanner is intentionally dependency-free so it can be invoked by
 * CI using only the JRE and by the local changed-service pipeline.</p>
 */
public final class ArchitectureScan {

    private static final Path ROOT = Path.of(".").toAbsolutePath().normalize();

    private static final List<Rule> RULES = List.of(
            new Rule(
                    "no-modular-monolith",
                    "Repository must not assert a modular-monolith or single-DB target outside the spec archive.",
                    Pattern.compile("(?i)\\b(modular[- ]monolith|single[- ]db|single database|one shared tree[- ]content (transaction|database))\\b"),
                    List.of(
                            ".kiro/specs/spring-boot-backend-migration",
                            "tooling/architecture-scan",
                            "node_modules",
                            ".git",
                            ".kilo",
                            ".idea"
                    )
            ),
            new Rule(
                    "no-cross-service-foreign-key",
                    "Flyway migrations must not declare foreign keys across service schemas.",
                    Pattern.compile("(?i)FOREIGN\\s+KEY.*REFERENCES\\s+services?[_-]?[a-z0-9]+"),
                    List.of("node_modules", ".git")
            ),
            new Rule(
                    "no-synchronous-success",
                    "Cross-service mutation controllers must not return 200/ResponseEntity.ok.",
                    Pattern.compile("ResponseEntity\\.ok\\(.*operationId"),
                    List.of("node_modules", ".git")
            )
    );

    public static void main(String[] args) throws IOException {
        int failures = 0;
        for (Rule rule : RULES) {
            List<Path> hits = scan(rule);
            if (hits.isEmpty()) {
                System.out.printf("[OK] %s — no violations%n", rule.name());
                continue;
            }
            failures += hits.size();
            System.err.printf("[FAIL] %s — %d violation(s):%n", rule.name(), hits.size());
            for (Path hit : hits) {
                System.err.println("  " + ROOT.relativize(hit));
            }
        }
        if (failures > 0) {
            System.err.printf("%nArchitecture scan failed: %d violation(s).%n", failures);
            System.exit(1);
        }
        System.out.println("Architecture scan passed.");
    }

    private static List<Path> scan(Rule rule) throws IOException {
        List<Path> hits = new ArrayList<>();
        Files.walk(ROOT)
                .filter(Files::isRegularFile)
                .filter(p -> {
                    for (String excluded : rule.excludedPrefixes()) {
                        Path abs = p.toAbsolutePath().normalize();
                        if (abs.toString().contains(excluded)) {
                            return false;
                        }
                    }
                    return true;
                })
                .forEach(p -> {
                    try {
                        String content = Files.readString(p);
                        if (rule.pattern().matcher(content).find()) {
                            hits.add(p);
                        }
                    } catch (IOException ignored) {
                        // unreadable files are skipped
                    }
                });
        return hits;
    }

    private record Rule(String name, String description, Pattern pattern, List<String> excludedPrefixes) { }
}
