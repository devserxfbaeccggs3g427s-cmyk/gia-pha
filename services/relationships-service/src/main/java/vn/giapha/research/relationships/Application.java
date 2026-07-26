package vn.giapha.research.relationships;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * relationships-service — microservice-decomposition phase.
 *
 * <p>This service owns its relationship domain, adapters, schema migrations,
 * authorization boundary and transactional outbox persistence.
 *
 * <p>See docs/microservice/notes.md for the decomposition rationale.
 */
@SpringBootApplication
@EnableScheduling
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

}
