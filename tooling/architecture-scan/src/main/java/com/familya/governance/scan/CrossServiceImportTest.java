package com.familya.governance.scan;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Rejects cross-service source and domain-model imports. A service under
 * {@code services/<a>} may not import anything under {@code services/<b>}.
 */
public final class CrossServiceImportTest {

    private static final Path ROOT = Path.of(".").toAbsolutePath().normalize();
    private static final Path SERVICES = ROOT.resolve("services");

    public static void main(String[] args) throws IOException {
        if (!Files.isDirectory(SERVICES)) {
            System.out.println("[SKIP] Cross-service import test: no services/ directory yet.");
            return;
        }
        List<String> violations = new ArrayList<>();
        try (var stream = Files.walk(SERVICES)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .forEach(p -> {
                        String self = selfService(p);
                        if (self == null) {
                            return;
                        }
                        try {
                            String content = Files.readString(p);
                            for (Path other : otherServices(self)) {
                                String marker = "services." + other.getFileName().toString();
                                if (content.contains(marker)) {
                                    violations.add(ROOT.relativize(p) + " imports " + marker);
                                }
                            }
                        } catch (IOException ignored) {
                        }
                    });
        }
        if (!violations.isEmpty()) {
            System.err.println("[FAIL] Cross-service import:");
            violations.forEach(v -> System.err.println("  " + v));
            System.exit(1);
        }
        System.out.println("[OK] Cross-service import: clean.");
    }

    private static String selfService(Path file) {
        Path p = file;
        while (p.getParent() != null && !p.getParent().equals(SERVICES)) {
            p = p.getParent();
        }
        if (p.getParent() == null) {
            return null;
        }
        return p.getFileName().toString();
    }

    private static List<Path> otherServices(String self) throws IOException {
        List<Path> out = new ArrayList<>();
        try (var stream = Files.list(SERVICES)) {
            stream.filter(Files::isDirectory)
                    .filter(p -> !p.getFileName().toString().equals(self))
                    .forEach(out::add);
        }
        return out;
    }
}
