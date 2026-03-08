package com.matcodem.fincore.fx.adapter.out.messaging;

import java.util.List;

import org.apache.avro.specific.SpecificRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import com.matcodem.fincore.fx.domain.event.DomainEvent;
import com.matcodem.fincore.fx.domain.port.out.FxEventPublisher;
import com.matcodem.fincore.fx.infrastructure.messaging.avro.AvroFxEventMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Publishes FX domain events to Kafka as Avro messages.
 * <p>
 * Topic naming:
 * fx.rate.published       -> fincore.fx.rate-published
 * fx.conversion.executed  -> fincore.fx.conversion-executed
 * fx.conversion.failed    -> fincore.fx.conversion-failed
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaFxEventPublisher implements FxEventPublisher {

	private final KafkaTemplate<String, Object> avroKafkaTemplate;
	private final AvroFxEventMapper avroFxEventMapper;

	private static final String TOPIC_PREFIX = "fincore.fx.";

	@Override
	public void publishAll(List<DomainEvent> events) {
		events.forEach(this::publish);
	}

	private void publish(DomainEvent event) {
		SpecificRecord avroRecord = avroFxEventMapper.toAvro(event);
		String topic = TOPIC_PREFIX + event.eventType().replace("fx.", "").replace(".", "-");
		String key = event.aggregateId();

		avroKafkaTemplate.send(topic, key, avroRecord)
				.whenComplete((result, ex) -> {
					if (ex != null) {
						log.error("Failed to publish FX Avro event {} to {}: {}",
								event.eventType(), topic, ex.getMessage());
					} else {
						log.debug("Published FX Avro event {} -> {} offset={}",
								event.eventType(), topic,
								result.getRecordMetadata().offset());
					}
				});
	}
}