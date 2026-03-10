package com.matcodem.fincore.fx.infrastructure.messaging;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.apache.avro.Schema;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.io.JsonDecoder;
import org.apache.avro.specific.SpecificDatumReader;
import org.apache.avro.specific.SpecificRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.matcodem.fincore.fx.infrastructure.persistence.entity.FxOutboxMessageJpaEntity;
import com.matcodem.fincore.fx.infrastructure.persistence.repository.FxOutboxJpaRepository;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class FxOutboxPoller {

	private static final int BATCH_SIZE = 100;
	private static final int MAX_RETRY_COUNT = 5;
	private static final String TOPIC_PREFIX = "fincore.fx.";

	private final FxOutboxJpaRepository outboxRepository;
	private final KafkaTemplate<String, Object> avroKafkaTemplate;
	private final MeterRegistry meterRegistry;

	private static final Map<String, Class<? extends SpecificRecord>> AVRO_CLASSES = Map.of(
			"fx.conversion.executed", com.matcodem.fincore.fx.avro.FxConversionExecutedEvent.class,
			"fx.conversion.failed", com.matcodem.fincore.fx.avro.FxConversionFailedEvent.class,
			"fx.rate.published", com.matcodem.fincore.fx.avro.ExchangeRatePublishedEvent.class
	);

	@Scheduled(fixedDelayString = "${fx.outbox.poller.fixed-delay-ms:500}")
	public void pollAndPublish() {
		List<FxOutboxMessageJpaEntity> pending = outboxRepository.findPendingForUpdate(BATCH_SIZE);
		if (pending.isEmpty()) return;

		log.debug("FX outbox poll: {} pending messages", pending.size());
		pending.forEach(this::publishOne);
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void publishOne(FxOutboxMessageJpaEntity message) {
		try {
			SpecificRecord avroRecord = fromAvroJson(message.getEventType(), message.getPayload());
			String topic = toTopic(message.getEventType());

			// avroKafkaTemplate uses KafkaAvroSerializer:
			//   -> registers schema with Schema Registry
			//   -> writes [0x00][4-byte schema ID][avro binary] - Confluent wire format
			//   -> consumers with KafkaAvroDeserializer receive correctly
			avroKafkaTemplate.send(topic, message.getAggregateId(), avroRecord).get();

			markSent(message);

			meterRegistry.counter("fx.outbox.published",
					"event_type", message.getEventType()).increment();

		} catch (Exception ex) {
			handleFailure(message, ex);
		}
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private SpecificRecord fromAvroJson(String eventType, String json) throws Exception {
		Class<? extends SpecificRecord> clazz = AVRO_CLASSES.get(eventType);
		if (clazz == null) {
			throw new IllegalStateException("No Avro class mapped for event type: " + eventType);
		}
		// Avro-generated classes expose their schema via static SCHEMA$ field
		Schema schema = (Schema) clazz.getField("SCHEMA$").get(null);
		JsonDecoder decoder = DecoderFactory.get().jsonDecoder(schema, json);
		return (SpecificRecord) new SpecificDatumReader(schema).read(null, decoder);
	}

	private String toTopic(String eventType) {
		return TOPIC_PREFIX + eventType.replace("fx.", "").replace(".", "-");
	}

	private void markSent(FxOutboxMessageJpaEntity message) {
		message.setStatus(FxOutboxMessageJpaEntity.Status.SENT);
		message.setProcessedAt(Instant.now());
		outboxRepository.save(message);
	}

	private void handleFailure(FxOutboxMessageJpaEntity message, Exception ex) {
		int newRetryCount = message.getRetryCount() + 1;
		message.setRetryCount(newRetryCount);

		if (newRetryCount >= MAX_RETRY_COUNT) {
			log.error("FX outbox message {} exceeded max retries - marking DEAD. eventType={} cause={}",
					message.getId(), message.getEventType(), ex.getMessage(), ex);
			message.setStatus(FxOutboxMessageJpaEntity.Status.DEAD);
			message.setProcessedAt(Instant.now());
			meterRegistry.counter("fx.outbox.dead",
					"event_type", message.getEventType()).increment();
		} else {
			log.warn("FX outbox publish failed (attempt {}/{}): id={} eventType={} - {}",
					newRetryCount, MAX_RETRY_COUNT,
					message.getId(), message.getEventType(), ex.getMessage());
			meterRegistry.counter("fx.outbox.retry",
					"event_type", message.getEventType()).increment();
		}

		outboxRepository.save(message);
	}
}