package vn.giapha.research.audit.config;

import java.nio.file.Path;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import vn.giapha.research.audit.application.service.HmacManifestSigner;
import vn.giapha.research.audit.application.service.ManifestSigner;
import vn.giapha.research.audit.support.TimeProvider;

@Configuration
public class AuditLocalConfiguration {
    @Bean
    TimeProvider timeProvider() {
        return TimeProvider.system();
    }

    @Bean
    AuditOperationsSettings auditOperationsSettings() {
        return new AuditOperationsSettings(100, Duration.ofSeconds(30), 5,
                Duration.ofSeconds(1), Duration.ofMinutes(5), Duration.ZERO,
                Duration.ofDays(90), Duration.ofDays(7), 1000);
    }

    @Bean
    Path stagingRoot(@Value("${audit.staging-root:${java.io.tmpdir}/giapha-audit}") String root) {
        return Path.of(root);
    }

    @Bean
    ManifestSigner manifestSigner(
            @Value("${audit.manifest-signing-secret:local-development-only}") String secret) {
        return new HmacManifestSigner(secret);
    }
}
