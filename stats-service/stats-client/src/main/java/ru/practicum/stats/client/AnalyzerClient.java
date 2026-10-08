package ru.practicum.stats.client;

import io.grpc.StatusRuntimeException;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;
import ru.practicum.ewm.stats.proto.InteractionsCountRequestProto;
import ru.practicum.ewm.stats.proto.RecommendationsControllerGrpc;
import ru.practicum.ewm.stats.proto.RecommendedEventProto;
import ru.practicum.ewm.stats.proto.SimilarEventsRequestProto;
import ru.practicum.ewm.stats.proto.UserPredictionsRequestProto;
import ru.practicum.stats.client.exception.StatsServerUnavailableException;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.StreamSupport;
import java.util.Spliterators;
import java.util.Spliterator;

@Component
public class AnalyzerClient {
    @GrpcClient("analyzer")
    private RecommendationsControllerGrpc.RecommendationsControllerBlockingStub client;

    public List<RecommendedEventProto> recommendations(long userId, int limit) {
        return collect(() -> stub().getRecommendationsForUser(UserPredictionsRequestProto.newBuilder()
                .setUserId(userId).setMaxResults(limit).build()));
    }

    public List<RecommendedEventProto> similarEvents(SimilarEventsRequestProto request) {
        return collect(() -> stub().getSimilarEvents(request));
    }

    public List<RecommendedEventProto> counts(List<Long> eventIds) {
        if (eventIds.isEmpty()) {
            return List.of();
        }
        return collect(() -> stub().getInteractionsCount(InteractionsCountRequestProto.newBuilder()
                .addAllEventId(eventIds).build()));
    }

    private RecommendationsControllerGrpc.RecommendationsControllerBlockingStub stub() {
        return client.withDeadlineAfter(5, TimeUnit.SECONDS);
    }

    private List<RecommendedEventProto> collect(Supplier<Iterator<RecommendedEventProto>> supplier) {
        try {
            return StreamSupport.stream(Spliterators.spliteratorUnknownSize(supplier.get(), Spliterator.ORDERED), false)
                    .toList();
        } catch (StatusRuntimeException exception) {
            throw new StatsServerUnavailableException("Analyzer is unavailable", exception);
        }
    }
}
