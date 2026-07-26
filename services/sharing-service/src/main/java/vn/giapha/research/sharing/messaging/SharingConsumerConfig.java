package vn.giapha.research.sharing.messaging;

import java.util.List;

import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SharingConsumerConfig {

    public static final String QUEUE = "sharing-service.public-projection";
    private static final List<String> EXCHANGES = List.of(
            "events.tree", "events.members", "events.relationships",
            "events.events", "events.media");

    @Bean
    Declarables sharingTopology() {
        Queue queue = QueueBuilder.durable(QUEUE)
                .withArgument("x-dead-letter-exchange", "events.dlx").build();
        java.util.ArrayList<org.springframework.amqp.core.Declarable> topology =
                new java.util.ArrayList<>();
        topology.add(queue);
        topology.add(new FanoutExchange("events.dlx", true, false));
        for (String name : EXCHANGES) {
            FanoutExchange exchange = new FanoutExchange(name, true, false);
            topology.add(exchange);
            topology.add(BindingBuilder.bind(queue).to(exchange));
        }
        return new Declarables(topology);
    }
}
