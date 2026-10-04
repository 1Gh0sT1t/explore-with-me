package ru.practicum.ewm.stats.kafka;

import org.apache.avro.io.DecoderFactory;
import org.apache.avro.specific.SpecificDatumReader;
import org.apache.avro.specific.SpecificRecord;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Deserializer;

import java.io.IOException;

public abstract class AvroDeserializer<T extends SpecificRecord> implements Deserializer<T> {
    private final Class<T> recordType;

    protected AvroDeserializer(Class<T> recordType) {
        this.recordType = recordType;
    }

    @Override
    public T deserialize(String topic, byte[] data) {
        if (data == null) {
            return null;
        }
        try {
            return new SpecificDatumReader<>(recordType)
                    .read(null, DecoderFactory.get().binaryDecoder(data, null));
        } catch (IOException | RuntimeException exception) {
            throw new SerializationException("Cannot deserialize Avro record", exception);
        }
    }
}
