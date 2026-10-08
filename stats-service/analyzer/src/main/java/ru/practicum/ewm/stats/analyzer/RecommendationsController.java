package ru.practicum.ewm.stats.analyzer;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import ru.practicum.ewm.stats.proto.InteractionsCountRequestProto;
import ru.practicum.ewm.stats.proto.RecommendationsControllerGrpc;
import ru.practicum.ewm.stats.proto.RecommendedEventProto;
import ru.practicum.ewm.stats.proto.SimilarEventsRequestProto;
import ru.practicum.ewm.stats.proto.UserPredictionsRequestProto;

import java.util.List;
import java.util.function.Supplier;

@Slf4j
@GrpcService
@RequiredArgsConstructor
public class RecommendationsController extends RecommendationsControllerGrpc.RecommendationsControllerImplBase {
    private final RecommendationService service;

    @Override
    public void getRecommendationsForUser(UserPredictionsRequestProto request,
                                         StreamObserver<RecommendedEventProto> response) {
        respond(response, () -> {
            validate(request.getUserId(), request.getMaxResults());
            return service.recommendations(request.getUserId(), request.getMaxResults());
        });
    }

    @Override
    public void getSimilarEvents(SimilarEventsRequestProto request, StreamObserver<RecommendedEventProto> response) {
        respond(response, () -> {
            validate(request.getUserId(), request.getMaxResults());
            if (request.getEventId() <= 0) {
                throw new IllegalArgumentException("Event id must be positive");
            }
            return service.similarEvents(request);
        });
    }

    @Override
    public void getInteractionsCount(InteractionsCountRequestProto request,
                                     StreamObserver<RecommendedEventProto> response) {
        respond(response, () -> {
            if (request.getEventIdList().stream().anyMatch(id -> id <= 0)) {
                throw new IllegalArgumentException("Event ids must be positive");
            }
            return service.counts(request.getEventIdList());
        });
    }

    private void validate(long userId, int limit) {
        if (userId <= 0 || limit <= 0 || limit > 1000) {
            throw new IllegalArgumentException("Positive user id and max_results between 1 and 1000 are required");
        }
    }

    private void respond(StreamObserver<RecommendedEventProto> response,
                         Supplier<List<RecommendedEventProto>> supplier) {
        try {
            supplier.get().forEach(response::onNext);
            response.onCompleted();
        } catch (IllegalArgumentException exception) {
            response.onError(Status.INVALID_ARGUMENT.withDescription(exception.getMessage()).asRuntimeException());
        } catch (Exception exception) {
            log.error("Cannot build recommendations", exception);
            response.onError(Status.INTERNAL.withDescription("Cannot build recommendations").asRuntimeException());
        }
    }
}
