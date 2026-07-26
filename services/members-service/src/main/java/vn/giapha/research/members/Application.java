package vn.giapha.research.members;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * members-service — microservice-decomposition phase.
 *
 * <p>This service owns its member domain, adapters, schema migrations,
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
