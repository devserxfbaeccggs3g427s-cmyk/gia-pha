package vn.giapha.research.sharing.messaging;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class SharingEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(SharingEventConsumer.class);

    @RabbitListener(queues = SharingConsumerConfig.QUEUE)
    public void onEvent(Map<String, Object> envelope) {
        log.info("sharing-service consumed: {} aggregate={}", envelope.get("eventType"),
                envelope.get("aggregateId"));
    }
}
