package ru.practicum.ewm.stats.collector;

import com.google.protobuf.Empty;
import com.google.protobuf.Timestamp;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.apache.avro.specific.SpecificRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import ru.practicum.ewm.stats.avro.ActionTypeAvro;
import ru.practicum.ewm.stats.avro.UserActionAvro;
import ru.practicum.ewm.stats.proto.ActionTypeProto;
import ru.practicum.ewm.stats.proto.UserActionProto;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class UserActionControllerTest {
    private final KafkaTemplate<String, SpecificRecord> kafka = mock();
    private final StreamObserver<Empty> response = mock();
    private final UserActionController controller = new UserActionController(kafka);

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(controller, "topic", "actions");
    }

    @Test
    void mapsGrpcActionToAvroAndWaitsForKafkaAcknowledgement() {
        when(kafka.send(eq("actions"), eq("2"), any(SpecificRecord.class)))
                .thenReturn(CompletableFuture.completedFuture(null));
        controller.collectUserAction(action().build(), response);
        ArgumentCaptor<SpecificRecord> captor = ArgumentCaptor.forClass(SpecificRecord.class);
        verify(kafka).send(eq("actions"), eq("2"), captor.capture());
        UserActionAvro action = (UserActionAvro) captor.getValue();
        assertThat(action.getUserId()).isEqualTo(1);
        assertThat(action.getActionType()).isEqualTo(ActionTypeAvro.LIKE);
        assertThat(action.getTimestamp()).isEqualTo(Instant.ofEpochSecond(100, 123000000));
        verify(response).onCompleted();
    }

    @Test
    void rejectsMissingTimestampBeforePublishing() {
        controller.collectUserAction(action().clearTimestamp().build(), response);
        verifyNoInteractions(kafka);
        assertStatus(Status.Code.INVALID_ARGUMENT);
    }

    @Test
    void rejectsUnknownActionTypeBeforePublishing() {
        controller.collectUserAction(action().setActionTypeValue(99).build(), response);
        verifyNoInteractions(kafka);
        assertStatus(Status.Code.INVALID_ARGUMENT);
    }

    @Test
    void reportsKafkaFailureWithoutSuccessfulResponse() {
        when(kafka.send(eq("actions"), eq("2"), any(SpecificRecord.class)))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("Broker unavailable")));
        controller.collectUserAction(action().build(), response);
        assertStatus(Status.Code.UNAVAILABLE);
    }

    private UserActionProto.Builder action() {
        return UserActionProto.newBuilder().setUserId(1).setEventId(2).setActionType(ActionTypeProto.ACTION_LIKE)
                .setTimestamp(Timestamp.newBuilder().setSeconds(100).setNanos(123000000));
    }

    private void assertStatus(Status.Code code) {
        ArgumentCaptor<Throwable> error = ArgumentCaptor.forClass(Throwable.class);
        verify(response).onError(error.capture());
        assertThat(Status.fromThrowable(error.getValue()).getCode()).isEqualTo(code);
    }
}
