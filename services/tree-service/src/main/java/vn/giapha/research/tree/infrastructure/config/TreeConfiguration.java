package vn.giapha.research.tree.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({TreeProperties.class, ServiceProperties.class})
public class TreeConfiguration {
    @Bean
    RestClient identityRestClient(RestClient.Builder builder,
            @Value("${identity-service.base-url:http://localhost:8081}") String baseUrl) {
        return builder.baseUrl(baseUrl).build();
    }
}
