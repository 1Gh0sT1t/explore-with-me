package ru.practicum.ewm.stats.analyzer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import ru.practicum.ewm.stats.proto.RecommendedEventProto;
import ru.practicum.ewm.stats.proto.SimilarEventsRequestProto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecommendationServiceTest {
    @Mock
    private RecommendationRepository repository;
    @InjectMocks
    private RecommendationService service;

    @Test
    void returnsEmptyForNewUser() {
        assertThat(service.recommendations(1, 10)).isEmpty();
    }

    @Test
    void predictsWeightedScoreFromAllSeenNeighbors() {
        when(repository.history(1)).thenReturn(List.of(interaction(2, 0.8), interaction(3, 0.4), interaction(4, 0.8)));
        when(repository.similaritiesForUser(1)).thenReturn(List.of(
                new Similarity(1, 2, 0.9), new Similarity(1, 3, 0.7), new Similarity(1, 4, 0.6)));
        var result = service.recommendations(1, 10);
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getEventId()).isEqualTo(1);
        assertThat(result.getFirst().getScore()).isCloseTo(1.48 / 2.2, within(1e-12));
    }

    @Test
    void excludesSeenEventsAndDeduplicatesCandidates() {
        when(repository.history(1)).thenReturn(List.of(interaction(1, 1), interaction(2, 0.8)));
        when(repository.similaritiesForUser(1)).thenReturn(List.of(
                new Similarity(1, 2, 0.9), new Similarity(1, 3, 0.7), new Similarity(2, 3, 0.6)));
        assertThat(service.recommendations(1, 10)).extracting(RecommendedEventProto::getEventId).containsExactly(3L);
    }

    @Test
    void usesMostRecentHistoryForCandidatesButAllHistoryForPrediction() {
        ReflectionTestUtils.setField(service, "historySize", 1);
        when(repository.history(1)).thenReturn(List.of(interaction(1, 1), interaction(2, 0.4)));
        when(repository.similaritiesForUser(1)).thenReturn(List.of(
                new Similarity(1, 3, 0.7), new Similarity(2, 3, 0.6), new Similarity(2, 4, 0.9)));
        var result = service.recommendations(1, 10);
        assertThat(result).extracting(RecommendedEventProto::getEventId).containsExactly(3L);
        assertThat(result.getFirst().getScore()).isCloseTo((0.7 + 0.24) / 1.3, within(1e-12));
    }

    @Test
    void respectsNeighborLimitAndCandidateLimit() {
        ReflectionTestUtils.setField(service, "neighbors", 1);
        when(repository.history(1)).thenReturn(List.of(interaction(1, 1), interaction(2, 0.4)));
        when(repository.similaritiesForUser(1)).thenReturn(List.of(
                new Similarity(1, 3, 0.7), new Similarity(2, 3, 0.9), new Similarity(1, 4, 0.8)));
        var result = service.recommendations(1, 1);
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getEventId()).isEqualTo(3L);
        assertThat(result.getFirst().getScore()).isEqualTo(0.4);
    }

    @Test
    void similarEventsHandlesBothSidesAndExcludesSeenTargets() {
        when(repository.history(1)).thenReturn(List.of(interaction(1, 1), interaction(3, 1)));
        when(repository.similaritiesForEvent(2)).thenReturn(List.of(
                new Similarity(1, 2, 0.7), new Similarity(2, 3, 0.9),
                new Similarity(2, 4, 0.4), new Similarity(2, 5, 0.6)));
        assertThat(service.similarEvents(SimilarEventsRequestProto.newBuilder()
                .setEventId(2).setUserId(1).setMaxResults(1).build()))
                .extracting(RecommendedEventProto::getEventId).containsExactly(5L);
    }

    @Test
    void countsIncludeZeroAndDeduplicateIds() {
        when(repository.interactionCounts(List.of(1L, 2L, 1L))).thenReturn(Map.of(1L, 1.8));
        var result = service.counts(List.of(1L, 2L, 1L));
        assertThat(result).extracting(RecommendedEventProto::getScore).containsExactly(1.8, 0.0);
    }

    @Test
    void sortsPredictionsByScoreInsteadOfSimilarity() {
        when(repository.history(1)).thenReturn(List.of(interaction(1, 0.4), interaction(2, 1)));
        when(repository.similaritiesForUser(1)).thenReturn(List.of(
                new Similarity(1, 3, 0.9), new Similarity(2, 4, 0.6)));
        assertThat(service.recommendations(1, 10)).extracting(RecommendedEventProto::getEventId)
                .containsExactly(4L, 3L);
    }

    private Interaction interaction(long event, double weight) {
        return new Interaction(event, weight, Instant.ofEpochSecond(100));
    }
}
