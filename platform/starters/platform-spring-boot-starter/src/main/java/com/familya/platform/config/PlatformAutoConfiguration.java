package com.familya.platform.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import javax.sql.DataSource;

@Configuration
@EnableConfigurationProperties(PlatformProperties.class)
public class PlatformAutoConfiguration {

    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        return new ObjectMapper().registerModule(new JavaTimeModule());
    }

    @Bean
    public NamedParameterJdbcTemplate namedParameterJdbcTemplate(DataSource ds) {
        return new NamedParameterJdbcTemplate(ds);
    }
}
