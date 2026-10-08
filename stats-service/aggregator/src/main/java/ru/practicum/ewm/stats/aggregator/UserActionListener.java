package ru.practicum.ewm.stats.aggregator;

import lombok.RequiredArgsConstructor;
import org.apache.avro.specific.SpecificRecord;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ConsumerSeekAware;
import org.springframework.stereotype.Component;
import ru.practicum.ewm.stats.avro.EventSimilarityAvro;
import ru.practicum.ewm.stats.avro.UserActionAvro;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class UserActionListener implements ConsumerSeekAware {
    private final SimilarityCalculator calculator;
    private final KafkaTemplate<String, SpecificRecord> kafkaTemplate;
    private List<EventSimilarityAvro> pending;

    @Value("${stats.topics.events-similarity}")
    private String topic;

    @KafkaListener(topics = "${stats.topics.user-actions}", groupId = "aggregator")
    public void consume(ConsumerRecord<String, UserActionAvro> record) throws Exception {
        if (pending == null) {
            pending = calculator.update(record.value());
        }
        for (EventSimilarityAvro similarity : pending) {
            kafkaTemplate.send(topic, similarity.getEventA() + ":" + similarity.getEventB(), similarity)
                    .get(15, TimeUnit.SECONDS);
        }
        pending = null;
    }

    @Override
    public void onPartitionsAssigned(Map<TopicPartition, Long> assignments, ConsumerSeekCallback callback) {
        // Состояние хранится в памяти, поэтому после назначения партиции восстанавливаем всю историю.
        calculator.clear();
        pending = null;
        callback.seekToBeginning(assignments.keySet());
    }
}
