package ru.practicum.ewm.stats.kafka;

import org.junit.jupiter.api.Test;
import org.apache.kafka.common.errors.SerializationException;
import ru.practicum.ewm.stats.avro.ActionTypeAvro;
import ru.practicum.ewm.stats.avro.EventSimilarityAvro;
import ru.practicum.ewm.stats.avro.UserActionAvro;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AvroSerializationTest {
    private final AvroSerializer serializer = new AvroSerializer();

    @Test
    void roundTripsUserActionWithLongIdsAndMilliseconds() {
        var action = new UserActionAvro(5_000_000_000L, 6_000_000_000L, ActionTypeAvro.LIKE,
                Instant.parse("2026-01-01T00:00:00.123Z"));
        assertThat(new UserActionDeserializer().deserialize("actions", serializer.serialize("actions", action)))
                .isEqualTo(action);
    }

    @Test
    void roundTripsSimilarityWithDoubleScore() {
        var similarity = new EventSimilarityAvro(1L, 2L, 0.2857142857142857, Instant.ofEpochMilli(500));
        assertThat(new EventSimilarityDeserializer().deserialize("pairs", serializer.serialize("pairs", similarity)))
                .isEqualTo(similarity);
    }

    @Test
    void supportsTombstones() {
        assertThat(serializer.serialize("actions", null)).isNull();
        assertThat(new UserActionDeserializer().deserialize("actions", null)).isNull();
    }

    @Test
    void rejectsMalformedRecord() {
        assertThatThrownBy(() -> new UserActionDeserializer().deserialize("actions", new byte[]{1}))
                .isInstanceOf(SerializationException.class);
    }
}
