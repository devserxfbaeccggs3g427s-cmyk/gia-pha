package vn.giapha.research.audit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * audit-service — microservice-decomposition phase.
 *
 * <p>This standalone service owns the audit schema and its local domain,
 * persistence, web, worker and messaging infrastructure.
 */
@SpringBootApplication
@EnableScheduling
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
