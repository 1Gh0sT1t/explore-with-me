package ru.practicum.ewm.stats.aggregator;

import org.apache.avro.specific.SpecificRecord;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ConsumerSeekAware;
import org.springframework.test.util.ReflectionTestUtils;
import ru.practicum.ewm.stats.avro.ActionTypeAvro;
import ru.practicum.ewm.stats.avro.EventSimilarityAvro;
import ru.practicum.ewm.stats.avro.UserActionAvro;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserActionListenerTest {
    private final SimilarityCalculator calculator = mock();
    private final KafkaTemplate<String, SpecificRecord> kafka = mock();
    private final UserActionListener listener = new UserActionListener(calculator, kafka);

    @Test
    void retriesPendingOutputWithoutUpdatingMatrixTwice() throws Exception {
        UserActionAvro action = new UserActionAvro(1L, 2L, ActionTypeAvro.VIEW, Instant.ofEpochSecond(100));
        var pair = new EventSimilarityAvro(1L, 2L, 0.5, action.getTimestamp());
        ReflectionTestUtils.setField(listener, "topic", "pairs");
        when(calculator.update(action)).thenReturn(List.of(pair));
        when(kafka.send(eq("pairs"), eq("1:2"), any(SpecificRecord.class)))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("Broker unavailable")))
                .thenReturn(CompletableFuture.completedFuture(null));
        ConsumerRecord<String, UserActionAvro> record = new ConsumerRecord<>("actions", 0, 0, "2", action);
        assertThatThrownBy(() -> listener.consume(record)).isInstanceOf(Exception.class);
        listener.consume(record);
        verify(calculator, times(1)).update(action);
        verify(kafka, times(2)).send("pairs", "1:2", pair);
    }

    @Test
    void resetsAndSeeksHistoryWhenPartitionIsAssigned() {
        var callback = mock(ConsumerSeekAware.ConsumerSeekCallback.class);
        var assignments = Map.of(new TopicPartition("actions", 0), 10L);
        listener.onPartitionsAssigned(assignments, callback);
        verify(calculator).clear();
        verify(callback).seekToBeginning(assignments.keySet());
    }
}
