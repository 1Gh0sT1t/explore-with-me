package ru.practicum.ewm.stats.aggregator;

import org.springframework.stereotype.Component;
import ru.practicum.ewm.stats.ActionWeights;
import ru.practicum.ewm.stats.avro.EventSimilarityAvro;
import ru.practicum.ewm.stats.avro.UserActionAvro;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class SimilarityCalculator {
    private final Map<Long, Map<Long, Double>> weights = new HashMap<>();
    private final Map<Long, Double> totals = new HashMap<>();
    private final Map<EventPair, Double> intersections = new HashMap<>();

    public List<EventSimilarityAvro> update(UserActionAvro action) {
        long event = action.getEventId();
        long user = action.getUserId();
        Map<Long, Double> users = weights.computeIfAbsent(event, key -> new HashMap<>());
        double previous = users.getOrDefault(user, 0.0);
        double current = ActionWeights.weight(action.getActionType());
        if (current <= previous) {
            return List.of();
        }
        users.put(user, current);
        totals.merge(event, current - previous, Double::sum);
        List<EventSimilarityAvro> result = new ArrayList<>();
        for (var other : weights.entrySet()) {
            if (other.getKey() == event) {
                continue;
            }
            EventPair pair = new EventPair(Math.min(event, other.getKey()), Math.max(event, other.getKey()));
            double otherWeight = other.getValue().getOrDefault(user, 0.0);
            double delta = Math.min(current, otherWeight) - Math.min(previous, otherWeight);
            double intersection = intersections.merge(pair, delta, Double::sum);
            double score = intersection / Math.sqrt(totals.get(event) * totals.get(other.getKey()));
            result.add(new EventSimilarityAvro(pair.first(), pair.second(), score, action.getTimestamp()));
        }
        return result;
    }

    public void clear() {
        weights.clear();
        totals.clear();
        intersections.clear();
    }

    private record EventPair(long first, long second) {
    }
}
