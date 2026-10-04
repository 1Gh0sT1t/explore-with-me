package ru.practicum.stats.client;

import com.google.protobuf.Empty;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import ru.practicum.ewm.stats.proto.ActionTypeProto;
import ru.practicum.ewm.stats.proto.InteractionsCountRequestProto;
import ru.practicum.ewm.stats.proto.RecommendationsControllerGrpc;
import ru.practicum.ewm.stats.proto.RecommendedEventProto;
import ru.practicum.ewm.stats.proto.SimilarEventsRequestProto;
import ru.practicum.ewm.stats.proto.UserActionControllerGrpc;
import ru.practicum.ewm.stats.proto.UserActionProto;
import ru.practicum.ewm.stats.proto.UserPredictionsRequestProto;
import ru.practicum.stats.client.exception.StatsServerUnavailableException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GrpcClientsTest {
    private Server server;
    private ManagedChannel channel;
    private final CollectorClient collector = new CollectorClient();
    private final AnalyzerClient analyzer = new AnalyzerClient();
    private final List<UserActionProto> actions = new ArrayList<>();

    @BeforeEach
    void setUp() throws IOException {
        String name = InProcessServerBuilder.generateName();
        server = InProcessServerBuilder.forName(name).directExecutor()
                .addService(new UserActionControllerGrpc.UserActionControllerImplBase() {
                    @Override
                    public void collectUserAction(UserActionProto request, StreamObserver<Empty> response) {
                        actions.add(request);
                        response.onNext(Empty.getDefaultInstance());
                        response.onCompleted();
                    }
                })
                .addService(new RecommendationsControllerGrpc.RecommendationsControllerImplBase() {
                    @Override
                    public void getRecommendationsForUser(UserPredictionsRequestProto request,
                                                         StreamObserver<RecommendedEventProto> response) {
                        response.onNext(event(10, 0.8));
                        response.onNext(event(20, 0.4));
                        response.onCompleted();
                    }

                    @Override
                    public void getSimilarEvents(SimilarEventsRequestProto request,
                                                 StreamObserver<RecommendedEventProto> response) {
                        response.onNext(event(30, 0.7));
                        response.onCompleted();
                    }

                    @Override
                    public void getInteractionsCount(InteractionsCountRequestProto request,
                                                     StreamObserver<RecommendedEventProto> response) {
                        response.onNext(event(10, 0.8));
                        response.onError(Status.UNAVAILABLE.asRuntimeException());
                    }
                }).build().start();
        channel = InProcessChannelBuilder.forName(name).directExecutor().build();
        ReflectionTestUtils.setField(collector, "client", UserActionControllerGrpc.newBlockingStub(channel));
        ReflectionTestUtils.setField(analyzer, "client", RecommendationsControllerGrpc.newBlockingStub(channel));
    }

    @AfterEach
    void tearDown() {
        channel.shutdownNow();
        server.shutdownNow();
    }

    @Test
    void collectorSendsEveryActionWithIdsAndTimestamp() {
        collector.view(1, 2);
        collector.register(1, 2);
        collector.like(1, 2);
        assertThat(actions).extracting(UserActionProto::getActionType).containsExactly(
                ActionTypeProto.ACTION_VIEW, ActionTypeProto.ACTION_REGISTER, ActionTypeProto.ACTION_LIKE);
        assertThat(actions).allSatisfy(action -> {
            assertThat(action.getUserId()).isEqualTo(1);
            assertThat(action.getEventId()).isEqualTo(2);
            assertThat(action.hasTimestamp()).isTrue();
        });
    }

    @Test
    void readsTheEntireRecommendationsStreamInOrder() {
        assertThat(analyzer.recommendations(1, 2)).extracting(RecommendedEventProto::getEventId)
                .containsExactly(10L, 20L);
    }

    @Test
    void readsSimilarEventsStream() {
        assertThat(analyzer.similarEvents(SimilarEventsRequestProto.newBuilder()
                .setEventId(2).setUserId(1).setMaxResults(1).build()))
                .extracting(RecommendedEventProto::getEventId).containsExactly(30L);
    }

    @Test
    void doesNotReturnPartialStreamWhenServerFails() {
        assertThatThrownBy(() -> analyzer.counts(List.of(10L))).isInstanceOf(StatsServerUnavailableException.class);
    }

    @Test
    void emptyCountsDoNotCallServer() {
        assertThat(analyzer.counts(List.of())).isEmpty();
    }

    @Test
    void collectorReportsUnavailableServer() {
        channel.shutdownNow();
        assertThatThrownBy(() -> collector.view(1, 2)).isInstanceOf(StatsServerUnavailableException.class);
    }

    private RecommendedEventProto event(long id, double score) {
        return RecommendedEventProto.newBuilder().setEventId(id).setScore(score).build();
    }
}
