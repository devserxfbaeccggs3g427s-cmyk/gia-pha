package vn.giapha.research.tree.infrastructure.outbox;

import java.time.Instant;
import java.util.UUID;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import vn.giapha.research.tree.infrastructure.config.ServiceProperties;
import vn.giapha.research.tree.infrastructure.messaging.MessagingConfiguration;

@Component
public class OutboxRelay {
    private final OutboxRowDao dao;
    private final ObjectMapper mapper;
    private final ServiceProperties properties;
    private final RabbitTemplate rabbit;

    public OutboxRelay(OutboxRowDao dao, ObjectMapper mapper, ServiceProperties properties,
            RabbitTemplate rabbit) {
        this.dao = dao;
        this.mapper = mapper;
        this.properties = properties;
        this.rabbit = rabbit;
    }

    public UUID append(String eventType, String aggregateId, Long treeKey, Object payload) {
        try {
            return dao.append(UUID.randomUUID(), properties.getName(), aggregateId, treeKey,
                    eventType, mapper.writeValueAsString(payload));
        } catch (Exception exception) {
            throw new IllegalStateException("outbox payload not serialisable", exception);
        }
    }

    public int publishBatch(int batchSize) {
        int published = 0;
        for (OutboxRowDao.Row row : dao.fetchUnpublished(batchSize)) {
            try {
                rabbit.convertAndSend(MessagingConfiguration.EXCHANGE, "", row.payloadJson());
                dao.markPublished(row.id(), Instant.now());
                published++;
            } catch (Exception exception) {
                dao.incrementAttempts(row.id());
            }
        }
        return published;
    }
}
