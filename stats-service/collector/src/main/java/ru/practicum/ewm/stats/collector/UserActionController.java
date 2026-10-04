package ru.practicum.ewm.stats.collector;

import com.google.protobuf.Empty;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import net.devh.boot.grpc.server.service.GrpcService;
import org.apache.avro.specific.SpecificRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import ru.practicum.ewm.stats.avro.ActionTypeAvro;
import ru.practicum.ewm.stats.avro.UserActionAvro;
import ru.practicum.ewm.stats.proto.UserActionControllerGrpc;
import ru.practicum.ewm.stats.proto.UserActionProto;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

@GrpcService
@RequiredArgsConstructor
public class UserActionController extends UserActionControllerGrpc.UserActionControllerImplBase {
    private final KafkaTemplate<String, SpecificRecord> kafkaTemplate;

    @Value("${stats.topics.user-actions}")
    private String topic;

    @Override
    public void collectUserAction(UserActionProto request, StreamObserver<Empty> response) {
        try {
            validate(request);
            UserActionAvro action = new UserActionAvro(request.getUserId(), request.getEventId(),
                    actionType(request), Instant.ofEpochSecond(request.getTimestamp().getSeconds(),
                    request.getTimestamp().getNanos()));
            kafkaTemplate.send(topic, Long.toString(request.getEventId()), action).get(15, TimeUnit.SECONDS);
            response.onNext(Empty.getDefaultInstance());
            response.onCompleted();
        } catch (IllegalArgumentException exception) {
            response.onError(Status.INVALID_ARGUMENT.withDescription(exception.getMessage()).asRuntimeException());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            response.onError(Status.UNAVAILABLE.withDescription("Collection interrupted").asRuntimeException());
        } catch (Exception exception) {
            response.onError(Status.UNAVAILABLE.withDescription("Cannot store user action").asRuntimeException());
        }
    }

    private void validate(UserActionProto request) {
        if (request.getUserId() <= 0 || request.getEventId() <= 0 || !request.hasTimestamp()) {
            throw new IllegalArgumentException("User, event and timestamp are required");
        }
        var time = request.getTimestamp();
        if (time.getSeconds() < -62135596800L || time.getSeconds() > 253402300799L
                || time.getNanos() < 0 || time.getNanos() > 999999999) {
            throw new IllegalArgumentException("Invalid timestamp");
        }
    }

    private ActionTypeAvro actionType(UserActionProto request) {
        return switch (request.getActionType()) {
            case ACTION_VIEW -> ActionTypeAvro.VIEW;
            case ACTION_REGISTER -> ActionTypeAvro.REGISTER;
            case ACTION_LIKE -> ActionTypeAvro.LIKE;
            case UNRECOGNIZED -> throw new IllegalArgumentException("Unknown action type");
        };
    }
}
