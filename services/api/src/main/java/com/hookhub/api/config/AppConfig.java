package com.hookhub.api.config;

import java.time.Duration;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

@Configuration
public class AppConfig {

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        return mapper;
    }
    
    /**
     * Configures RestTemplate for webhook delivery with timeouts.
     * 
     * Timeout configuration:
     * - Connect timeout: 5 seconds
     * - Read timeout: 10 seconds
     */
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);  // 5 seconds
        factory.setReadTimeout(10000);   // 10 seconds
        
        return builder
                .requestFactory(() -> factory)
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(10))
                .build();
    }
    
    /**
     * Configures retry policy for webhook delivery.
     * 
     * Retry configuration:
     * - Base delay: 1 second
     * - Max delay: 60 seconds
     * - Max retries: 5
     */
    @Bean
    public com.hookhub.api.worker.RetryPolicy retryPolicy() {
        return new com.hookhub.api.worker.RetryPolicy(1000, 60000, 5);
    }
}

