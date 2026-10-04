package ru.practicum.ewm.stats.analyzer;

import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import ru.practicum.ewm.stats.avro.EventSimilarityAvro;
import ru.practicum.ewm.stats.avro.UserActionAvro;

@Component
@RequiredArgsConstructor
public class StatsListener {
    private final RecommendationRepository repository;

    @KafkaListener(topics = "${stats.topics.user-actions}", groupId = "analyzer-actions",
            containerFactory = "actionsFactory")
    public void consumeAction(UserActionAvro action) {
        repository.saveAction(action);
    }

    @KafkaListener(topics = "${stats.topics.events-similarity}", groupId = "analyzer-similarities",
            containerFactory = "similaritiesFactory")
    public void consumeSimilarity(EventSimilarityAvro similarity) {
        repository.saveSimilarity(similarity);
    }
}
