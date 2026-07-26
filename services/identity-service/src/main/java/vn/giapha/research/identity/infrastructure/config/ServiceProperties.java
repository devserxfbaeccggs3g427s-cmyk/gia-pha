package vn.giapha.research.identity.infrastructure.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "service")
public class ServiceProperties {
    private String name = "identity-service";
    private int outboxBatchSize = 100;
    private Duration outboxPollInterval = Duration.ofSeconds(1);

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getOutboxBatchSize() {
        return outboxBatchSize;
    }

    public void setOutboxBatchSize(int outboxBatchSize) {
        this.outboxBatchSize = outboxBatchSize;
    }

    public Duration getOutboxPollInterval() {
        return outboxPollInterval;
    }

    public void setOutboxPollInterval(Duration outboxPollInterval) {
        this.outboxPollInterval = outboxPollInterval;
    }
}
