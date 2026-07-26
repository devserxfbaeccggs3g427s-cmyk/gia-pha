package vn.giapha.research.binarystorage.config;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import vn.giapha.research.binarystorage.application.port.out.MalwareScanner;
import vn.giapha.research.binarystorage.application.port.out.UploadActivationHandler;
import vn.giapha.research.binarystorage.application.service.BinaryReplicationService.BinaryArchiveClient;
import vn.giapha.research.binarystorage.domain.model.ScanOutcome;
import vn.giapha.research.binarystorage.shared.time.TimeProvider;

@Configuration
class LocalInfrastructureConfiguration {

    @Bean
    TimeProvider timeProvider() {
        return TimeProvider.system();
    }

    @Bean
    MalwareScanner malwareScanner(TimeProvider timeProvider) {
        return (pathname, contentType, content) -> new ScanOutcome(
                ScanOutcome.Result.CLEAN,
                "local-scanner",
                "1",
                timeProvider.now());
    }

    @Bean
    UploadActivationHandler uploadActivationHandler() {
        return (intent, finalHead) -> {
        };
    }

    @Bean
    BinaryArchiveClient binaryArchiveClient() {
        Map<String, byte[]> objects = new ConcurrentHashMap<>();
        return new BinaryArchiveClient() {
            @Override
            public byte[] put(String archivePath, byte[] payload) {
                objects.put(archivePath, payload.clone());
                try {
                    return MessageDigest.getInstance("SHA-256").digest(payload);
                } catch (NoSuchAlgorithmException e) {
                    throw new IllegalStateException("SHA-256 is unavailable", e);
                }
            }

            @Override
            public byte[] get(String archivePath) {
                byte[] payload = objects.get(archivePath);
                return payload == null ? null : payload.clone();
            }
        };
    }
}
