package vn.giapha.research.transfer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * transfer-service — microservice-decomposition phase.
 *
 * <p>This standalone service owns the transfer schema and its local domain,
 * persistence and web infrastructure.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
