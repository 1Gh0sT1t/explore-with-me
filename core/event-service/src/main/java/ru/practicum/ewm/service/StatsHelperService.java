package ru.practicum.ewm.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.practicum.ewm.model.Event;
import ru.practicum.ewm.stats.proto.RecommendedEventProto;
import ru.practicum.stats.client.AnalyzerClient;
import ru.practicum.stats.client.CollectorClient;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StatsHelperService {
    private final CollectorClient collector;
    private final AnalyzerClient analyzer;

    public Map<Long, Double> getRatings(Collection<Event> events) {
        if (events.isEmpty()) {
            return Map.of();
        }
        return analyzer.counts(events.stream().map(Event::getId).distinct().toList()).stream()
                .collect(Collectors.toMap(RecommendedEventProto::getEventId, RecommendedEventProto::getScore));
    }

    public void view(long userId, long eventId) {
        collector.view(userId, eventId);
    }

    public void like(long userId, long eventId) {
        collector.like(userId, eventId);
    }

    public List<RecommendedEventProto> recommendations(long userId, int size) {
        return analyzer.recommendations(userId, size);
    }
}
