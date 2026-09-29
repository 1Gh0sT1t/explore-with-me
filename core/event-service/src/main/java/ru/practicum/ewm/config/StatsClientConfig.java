package ru.practicum.ewm.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.backoff.FixedBackOffPolicy;
import org.springframework.retry.policy.MaxAttemptsRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import ru.practicum.stats.client.StatsClient;

@Configuration
public class StatsClientConfig {

    @Bean
    public StatsClient statsClient(DiscoveryClient discoveryClient,
                                   @Value("${stats-server.service-id:stats-server}") String statsServiceId,
                                   RetryTemplate statsRetryTemplate) {
        return new StatsClient(discoveryClient, statsServiceId, statsRetryTemplate);
    }

    @Bean
    public RetryTemplate statsRetryTemplate(@Value("${stats-server.retry.max-attempts:3}") int maxAttempts,
                                            @Value("${stats-server.retry.backoff-period:3000}") long backoffPeriod) {
        FixedBackOffPolicy backOffPolicy = new FixedBackOffPolicy();
        backOffPolicy.setBackOffPeriod(backoffPeriod);

        RetryTemplate retryTemplate = new RetryTemplate();
        retryTemplate.setBackOffPolicy(backOffPolicy);
        retryTemplate.setRetryPolicy(new MaxAttemptsRetryPolicy(maxAttempts));
        return retryTemplate;
    }

    @Bean
    public String appName(@Value("${spring.application.name:event-service}") String appName) {
        return appName;
    }
}
