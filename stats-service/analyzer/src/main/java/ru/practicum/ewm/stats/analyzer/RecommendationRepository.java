package ru.practicum.ewm.stats.analyzer;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.ewm.stats.ActionWeights;
import ru.practicum.ewm.stats.avro.EventSimilarityAvro;
import ru.practicum.ewm.stats.avro.UserActionAvro;

import java.sql.Timestamp;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@Repository
@RequiredArgsConstructor
public class RecommendationRepository {
    private final NamedParameterJdbcTemplate jdbc;

    @Transactional
    public void saveAction(UserActionAvro action) {
        jdbc.update("""
                INSERT INTO user_interactions (user_id, event_id, weight, interacted_at)
                VALUES (:user, :event, :weight, :time)
                ON CONFLICT (user_id, event_id) DO UPDATE
                SET weight = GREATEST(user_interactions.weight, EXCLUDED.weight),
                    interacted_at = GREATEST(user_interactions.interacted_at, EXCLUDED.interacted_at)
                """, Map.of("user", action.getUserId(), "event", action.getEventId(),
                "weight", ActionWeights.weight(action.getActionType()), "time", Timestamp.from(action.getTimestamp())));
    }

    @Transactional
    public void saveSimilarity(EventSimilarityAvro similarity) {
        jdbc.update("""
                INSERT INTO event_similarities (event_a, event_b, score, updated_at)
                VALUES (:first, :second, :score, :time)
                ON CONFLICT (event_a, event_b) DO UPDATE
                SET score = EXCLUDED.score, updated_at = EXCLUDED.updated_at
                """, Map.of("first", Math.min(similarity.getEventA(), similarity.getEventB()),
                "second", Math.max(similarity.getEventA(), similarity.getEventB()),
                "score", similarity.getScore(), "time", Timestamp.from(similarity.getTimestamp())));
    }

    public List<Interaction> history(long userId) {
        return jdbc.query("""
                SELECT event_id, weight, interacted_at FROM user_interactions
                WHERE user_id = :user ORDER BY interacted_at DESC, event_id
                """, Map.of("user", userId), (row, index) -> new Interaction(row.getLong("event_id"),
                row.getDouble("weight"), row.getTimestamp("interacted_at").toInstant()));
    }

    public List<Similarity> similaritiesForUser(long userId) {
        return jdbc.query("""
                SELECT event_a, event_b, score FROM event_similarities
                WHERE score > 0 AND (
                    event_a IN (SELECT event_id FROM user_interactions WHERE user_id = :user)
                    OR event_b IN (SELECT event_id FROM user_interactions WHERE user_id = :user)
                )
                """, Map.of("user", userId), (row, index) -> new Similarity(row.getLong("event_a"),
                row.getLong("event_b"), row.getDouble("score")));
    }

    public List<Similarity> similaritiesForEvent(long eventId) {
        return jdbc.query("""
                SELECT event_a, event_b, score FROM event_similarities
                WHERE score > 0 AND (event_a = :event OR event_b = :event)
                """, Map.of("event", eventId), (row, index) -> new Similarity(row.getLong("event_a"),
                row.getLong("event_b"), row.getDouble("score")));
    }

    public Map<Long, Double> interactionCounts(Collection<Long> eventIds) {
        if (eventIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Double> result = new java.util.HashMap<>();
        jdbc.query("""
                SELECT event_id, SUM(weight) AS total FROM user_interactions
                WHERE event_id IN (:events) GROUP BY event_id
                """, Map.of("events", eventIds), row -> {
                    result.put(row.getLong("event_id"), row.getDouble("total"));
                });
        return result;
    }
}
