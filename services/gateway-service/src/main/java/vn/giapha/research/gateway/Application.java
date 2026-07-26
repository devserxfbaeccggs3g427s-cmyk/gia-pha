package vn.giapha.research.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * API Gateway (Spring Cloud Gateway). Phase 1.3.
 *
 * <p>Routes:
 * <ul>
 *   <li>/identity/**    -> identity-service</li>
 *   <li>/audit/**       -> audit-service</li>
 *   <li>/tree/**        -> tree-service</li>
 *   <li>/members/**     -> members-service</li>
 *   <li>/relationships/**-> relationships-service</li>
 *   <li>/events/**      -> events-service</li>
 *   <li>/media/**       -> media-metadata-service</li>
 *   <li>/binary/**      -> binary-storage-service</li>
 *   <li>/share/**       -> sharing-service</li>
 *   <li>/reporting/**   -> reporting-service</li>
 *   <li>/transfer/**    -> transfer-service</li>
 * </ul>
 *
 * <p>Auth: validates the session cookie via identity-service on every
 * request; forwards a short-lived JWT (≤ 5 min) as
 * {@code X-User-Context-Token}. See design.md §Security Model.
 */
@SpringBootApplication
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
