package vn.giapha.research.identity.infrastructure.outbox;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import vn.giapha.research.identity.infrastructure.config.ServiceProperties;

@Component
public class OutboxScheduler {
    private final OutboxRelay relay;
    private final int batchSize;

    public OutboxScheduler(OutboxRelay relay, ServiceProperties properties) {
        this.relay = relay;
        this.batchSize = properties.getOutboxBatchSize();
    }

    @Scheduled(fixedDelayString = "${service.outbox-poll-interval:1s}")
    public void publish() {
        relay.publishBatch(batchSize);
    }
}
