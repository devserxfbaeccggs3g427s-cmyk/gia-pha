package vn.giapha.research.audit.messaging;

import java.util.ArrayList;
import java.util.List;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AuditConsumerConfig {
    public static final String QUEUE = "audit-service.audit-events";
    private static final List<String> EXCHANGES = List.of(
            "events.identity", "events.tree", "events.members", "events.relationships",
            "events.events", "events.media", "events.binary", "events.sharing",
            "events.transfer", "events.reporting");

    @Bean
    Queue auditQueue() {
        return QueueBuilder.durable(QUEUE)
                .withArgument("x-dead-letter-exchange", "events.dlx")
                .build();
    }

    @Bean
    List<FanoutExchange> auditExchanges() {
        List<FanoutExchange> exchanges = new ArrayList<>();
        EXCHANGES.forEach(name -> exchanges.add(new FanoutExchange(name, true, false)));
        exchanges.add(new FanoutExchange("events.audit", true, false));
        exchanges.add(new FanoutExchange("events.dlx", true, false));
        return exchanges;
    }

    @Bean
    List<Binding> auditBindings(Queue auditQueue, List<FanoutExchange> auditExchanges) {
        return auditExchanges.stream()
                .filter(exchange -> EXCHANGES.contains(exchange.getName()))
                .map(exchange -> BindingBuilder.bind(auditQueue).to(exchange))
                .toList();
    }
}
