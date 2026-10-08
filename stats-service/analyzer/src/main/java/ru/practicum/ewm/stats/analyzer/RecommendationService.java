package ru.practicum.ewm.stats.analyzer;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ru.practicum.ewm.stats.proto.RecommendedEventProto;
import ru.practicum.ewm.stats.proto.SimilarEventsRequestProto;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RecommendationService {
    private final RecommendationRepository repository;

    @Value("${stats.recommendations.history-size:100}")
    private int historySize = 100;

    @Value("${stats.recommendations.neighbors:10}")
    private int neighbors = 10;

    public List<RecommendedEventProto> recommendations(long userId, int limit) {
        List<Interaction> history = repository.history(userId);
        if (history.isEmpty()) {
            return List.of();
        }
        Map<Long, Double> weights = history.stream()
                .collect(Collectors.toMap(Interaction::eventId, Interaction::weight));
        Set<Long> recent = history.stream().limit(historySize).map(Interaction::eventId).collect(Collectors.toSet());
        List<Similarity> similarities = repository.similaritiesForUser(userId);
        Map<Long, Double> candidates = new HashMap<>();
        for (Similarity similarity : similarities) {
            addCandidate(similarity, new CandidateContext(recent, weights, candidates));
        }
        List<RecommendedEventProto> result = new ArrayList<>();
        for (long candidate : candidates.keySet()) {
            List<Similarity> closest = similarities.stream()
                    .filter(pair -> pair.eventA() == candidate || pair.eventB() == candidate)
                    .filter(pair -> weights.containsKey(pair.other(candidate)))
                    .sorted(Comparator.comparingDouble(Similarity::score).reversed())
                    .limit(neighbors).toList();
            double denominator = closest.stream().mapToDouble(Similarity::score).sum();
            double numerator = closest.stream()
                    .mapToDouble(pair -> pair.score() * weights.get(pair.other(candidate))).sum();
            if (denominator > 0) {
                result.add(event(candidate, numerator / denominator));
            }
        }
        return result.stream().sorted(order()).limit(limit).toList();
    }

    public List<RecommendedEventProto> similarEvents(SimilarEventsRequestProto request) {
        Set<Long> seen = repository.history(request.getUserId()).stream()
                .map(Interaction::eventId).collect(Collectors.toSet());
        return repository.similaritiesForEvent(request.getEventId()).stream()
                .filter(pair -> !seen.contains(pair.other(request.getEventId())))
                .map(pair -> event(pair.other(request.getEventId()), pair.score()))
                .sorted(order()).limit(request.getMaxResults()).toList();
    }

    public List<RecommendedEventProto> counts(List<Long> eventIds) {
        Map<Long, Double> counts = repository.interactionCounts(eventIds);
        return eventIds.stream().distinct().map(id -> event(id, counts.getOrDefault(id, 0.0))).toList();
    }

    private void addCandidate(Similarity pair, CandidateContext context) {
        if (context.recent().contains(pair.eventA()) && !context.weights().containsKey(pair.eventB())) {
            context.candidates().merge(pair.eventB(), pair.score(), Math::max);
        }
        if (context.recent().contains(pair.eventB()) && !context.weights().containsKey(pair.eventA())) {
            context.candidates().merge(pair.eventA(), pair.score(), Math::max);
        }
    }

    private RecommendedEventProto event(long eventId, double score) {
        return RecommendedEventProto.newBuilder().setEventId(eventId).setScore(score).build();
    }

    private Comparator<RecommendedEventProto> order() {
        return Comparator.comparingDouble(RecommendedEventProto::getScore).reversed()
                .thenComparingLong(RecommendedEventProto::getEventId);
    }

    private record CandidateContext(Set<Long> recent, Map<Long, Double> weights, Map<Long, Double> candidates) {
    }
}
