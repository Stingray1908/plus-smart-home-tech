package ru.yandex.practicum.config;

import org.apache.avro.Schema;
import org.apache.avro.io.BinaryDecoder;
import org.apache.avro.io.DatumReader;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.specific.SpecificDatumReader;
import org.apache.avro.specific.SpecificRecordBase;
import org.apache.kafka.common.serialization.Deserializer;

import java.nio.ByteBuffer;

public class BaseAvroDeserializer<T extends SpecificRecordBase> implements Deserializer<T> {

    private final DecoderFactory decoderFactory;
    private final DatumReader<T> reader;

    public BaseAvroDeserializer(Schema schema) {
        this(DecoderFactory.get(), schema);
    }

    public BaseAvroDeserializer(DecoderFactory decoderFactory, Schema schema) {
        this.decoderFactory = decoderFactory;
        this.reader = new SpecificDatumReader<>(schema);
    }

    @Override
    public T deserialize(String topic, byte[] data) {
        if (data == null || data.length == 0) {
            return null;
        }
        try {
            BinaryDecoder decoder = decoderFactory.binaryDecoder(data, null);
            // Первый аргумент null означает, что мы не переиспользуем существующий объект
            return reader.read(null, decoder);
        } catch (Exception e) {
            throw new RuntimeException (
                    "Ошибка десериализации данных из топика [" + topic + "]", e
            );
        }
    }

    @Override
    public T deserialize(String topic, org.apache.kafka.common.header.Headers headers, byte[] data) {
        return deserialize(topic, data);
    }

    @Override
    public T deserialize(String topic, org.apache.kafka.common.header.Headers headers, ByteBuffer data) {
        if (data == null) {
            return null;
        }
        byte[] bytes = new byte[data.remaining()];
        data.duplicate().get(bytes);
        return deserialize(topic, bytes);
    }

}
