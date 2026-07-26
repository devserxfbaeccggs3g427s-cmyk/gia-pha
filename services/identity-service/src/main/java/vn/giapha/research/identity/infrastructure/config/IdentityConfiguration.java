package vn.giapha.research.identity.infrastructure.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({ResearchProperties.class, ServiceProperties.class})
public class IdentityConfiguration {}
