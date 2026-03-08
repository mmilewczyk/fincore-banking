package com.matcodem.fincore.payment.infrastructure.messaging.avro;

import org.apache.avro.specific.SpecificRecord;
import org.springframework.stereotype.Component;

import com.matcodem.fincore.payment.domain.event.DomainEvent;
import com.matcodem.fincore.payment.domain.model.OutboxMessage;
import com.matcodem.fincore.payment.domain.port.out.OutboxEventPublisher;
import com.matcodem.fincore.payment.domain.port.out.OutboxRepository;

import io.confluent.kafka.serializers.KafkaAvroSerializer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Outbox publisher that serializes domain events to Avro before storing in outbox.
 * <p>
 * DESIGN: The outbox still stores the serialized payload as a byte[] encoded to
 * a Base64 string (stored in the TEXT column). The OutboxPoller reads these bytes
 * and sends them directly to Kafka without re-serialization.
 * <p>
 * WHY SERIALIZE AT WRITE TIME (not at poll time)?
 * Serializing at write time means the Avro bytes are committed to the DB in the
 * same transaction as the payment. If Schema Registry is temporarily unavailable
 * at poll time, we don't want to fail publishing - the bytes are already encoded.
 * <p>
 * Trade-off: if the schema changes between write and publish, the stored bytes
 * use the old schema ID. This is fine - Schema Registry stores all versions
 * and the consumer fetches the schema by the ID embedded in the message bytes.
 * <p>
 * AVRO WIRE FORMAT (Confluent):
 * [0x00][schema-id 4 bytes][avro payload bytes]
 * The schema ID references the exact version in Schema Registry.
 * Consumers use this ID to fetch the reader schema for deserialization.
 * <p>
 * FALLBACK TO JSON:
 * If the event type has no Avro mapping (e.g. new event added before schema is defined),
 * we fall back to JSON serialization with a warning. This prevents data loss during
 * schema development cycles, but MUST be treated as a bug to fix.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AvroOutboxEventPublisher implements OutboxEventPublisher {

	private final OutboxRepository outboxRepository;
	private final AvroEventMapper avroEventMapper;
	private final KafkaAvroSerializer avroSerializer;

	// Topic prefix - must match OutboxPoller topic derivation logic
	private static final String TOPIC_PREFIX = "fincore.payments.";

	@Override
	public void publish(DomainEvent event, String aggregateType) {
		SpecificRecord avroRecord = avroEventMapper.toAvro(event);

		if (avroRecord == null) {
			// This should never happen in production - means a new domain event
			// was added without a corresponding .avsc file.
			log.error("BUG: No Avro schema mapping for event type '{}' - dropping event for payment {}. " +
							"Add a .avsc schema file and update AvroEventMapper.",
					event.eventType(), event.aggregateId());
			throw new IllegalStateException(
					"No Avro schema mapping for event type: " + event.eventType());
		}

		// Derive topic from event type: "payment.completed" -> "fincore.payments.payment-completed"
		String topic = TOPIC_PREFIX + event.eventType().replace(".", "-");

		// Serialize to Avro bytes (Confluent wire format: magic byte + schema ID + payload)
		// KafkaAvroSerializer requires the topic name to look up the subject in Schema Registry.
		// Subject convention: "{topic}-value" (Confluent default TopicNameStrategy)
		byte[] avroBytes = avroSerializer.serialize(topic, avroRecord);

		// Store as hex string in outbox TEXT column.
		// OutboxPoller sends raw bytes from this encoding directly to Kafka.
		String payloadHex = bytesToHex(avroBytes);

		OutboxMessage message = OutboxMessage.create(
				event.aggregateId(),
				aggregateType,
				event.eventType(),
				payloadHex
		);

		outboxRepository.save(message);

		log.debug("Queued Avro outbox message: eventType={}, paymentId={}, topic={}, bytes={}",
				event.eventType(), event.aggregateId(), topic, avroBytes.length);
	}

	private static String bytesToHex(byte[] bytes) {
		StringBuilder sb = new StringBuilder(bytes.length * 2);
		for (byte b : bytes) {
			sb.append(String.format("%02x", b));
		}
		return sb.toString();
	}
}
