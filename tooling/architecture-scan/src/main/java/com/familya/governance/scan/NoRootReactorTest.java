package com.familya.governance.scan;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rejects the introduction of a root Maven reactor that would compile
 * all services in a single build. Each service must own its own build
 * (ADR-011).
 */
public final class NoRootReactorTest {

    private static final Path ROOT = Path.of(".").toAbsolutePath().normalize();

    public static void main(String[] args) throws IOException {
        List<Path> poms = new ArrayList<>();
        try (var stream = Files.walk(ROOT)) {
            stream.filter(p -> p.getFileName().toString().equals("pom.xml"))
                    .forEach(poms::add);
        }
        List<String> violations = new ArrayList<>();
        Pattern modules = Pattern.compile("<modules>\\s*</modules>|<modules>.*?</modules>", Pattern.DOTALL);
        for (Path pom : poms) {
            String rel = ROOT.relativize(pom).toString();
            if (rel.startsWith("services/") || rel.startsWith("platform/starters/")) {
                continue;
            }
            String content = Files.readString(pom);
            Matcher m = modules.matcher(content);
            if (m.find()) {
                violations.add(rel);
            }
        }
        if (!violations.isEmpty()) {
            System.err.println("[FAIL] No-root-reactor: forbidden <modules> in: " + violations);
            System.exit(1);
        }
        System.out.println("[OK] No-root-reactor: no forbidden <modules> declarations.");
    }
}
