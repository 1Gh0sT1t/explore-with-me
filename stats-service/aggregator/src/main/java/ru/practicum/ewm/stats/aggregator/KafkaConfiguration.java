package ru.practicum.ewm.stats.aggregator;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConfiguration {
    @Bean
    public DefaultErrorHandler kafkaErrorHandler() {
        return new DefaultErrorHandler(new FixedBackOff(1000, FixedBackOff.UNLIMITED_ATTEMPTS));
    }
}
