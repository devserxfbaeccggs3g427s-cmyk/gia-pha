package vn.giapha.research.transfer.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "giapha.research.limits")
public record TransferProperties(long importMaxBytes) {
    public TransferProperties {
        if (importMaxBytes <= 0) {
            importMaxBytes = 10L * 1024 * 1024;
        }
    }
}
