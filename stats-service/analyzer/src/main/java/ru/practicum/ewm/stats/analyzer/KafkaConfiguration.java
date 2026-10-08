package ru.practicum.ewm.stats.analyzer;

import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;
import ru.practicum.ewm.stats.avro.EventSimilarityAvro;
import ru.practicum.ewm.stats.avro.UserActionAvro;
import ru.practicum.ewm.stats.kafka.EventSimilarityDeserializer;
import ru.practicum.ewm.stats.kafka.UserActionDeserializer;

import java.util.HashMap;

@Configuration
@RequiredArgsConstructor
public class KafkaConfiguration {
    private final KafkaProperties properties;

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, UserActionAvro> actionsFactory() {
        return factory(UserActionDeserializer.class);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, EventSimilarityAvro> similaritiesFactory() {
        return factory(EventSimilarityDeserializer.class);
    }

    private <T> ConcurrentKafkaListenerContainerFactory<String, T> factory(Class<?> deserializer) {
        var settings = new HashMap<>(properties.buildConsumerProperties(null));
        settings.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, deserializer);
        var factory = new ConcurrentKafkaListenerContainerFactory<String, T>();
        factory.setConsumerFactory(new DefaultKafkaConsumerFactory<>(settings));
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        factory.setCommonErrorHandler(new DefaultErrorHandler(new FixedBackOff(1000, FixedBackOff.UNLIMITED_ATTEMPTS)));
        return factory;
    }
}
