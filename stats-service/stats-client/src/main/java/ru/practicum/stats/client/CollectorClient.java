package ru.practicum.stats.client;

import com.google.protobuf.Timestamp;
import io.grpc.StatusRuntimeException;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;
import ru.practicum.ewm.stats.proto.ActionTypeProto;
import ru.practicum.ewm.stats.proto.UserActionControllerGrpc;
import ru.practicum.ewm.stats.proto.UserActionProto;
import ru.practicum.stats.client.exception.StatsServerUnavailableException;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

@Component
public class CollectorClient {
    @GrpcClient("collector")
    private UserActionControllerGrpc.UserActionControllerBlockingStub client;

    public void view(long userId, long eventId) {
        collect(action(ActionTypeProto.ACTION_VIEW).setUserId(userId).setEventId(eventId).build());
    }

    public void register(long userId, long eventId) {
        collect(action(ActionTypeProto.ACTION_REGISTER).setUserId(userId).setEventId(eventId).build());
    }

    public void like(long userId, long eventId) {
        collect(action(ActionTypeProto.ACTION_LIKE).setUserId(userId).setEventId(eventId).build());
    }

    private UserActionProto.Builder action(ActionTypeProto type) {
        Instant now = Instant.now();
        return UserActionProto.newBuilder().setActionType(type)
                .setTimestamp(Timestamp.newBuilder().setSeconds(now.getEpochSecond()).setNanos(now.getNano()));
    }

    public void collect(UserActionProto action) {
        try {
            client.withDeadlineAfter(20, TimeUnit.SECONDS).collectUserAction(action);
        } catch (StatusRuntimeException exception) {
            throw new StatsServerUnavailableException("Collector is unavailable", exception);
        }
    }
}
