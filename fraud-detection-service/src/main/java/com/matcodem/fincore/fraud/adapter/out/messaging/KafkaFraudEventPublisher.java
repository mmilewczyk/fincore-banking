package com.matcodem.fincore.fraud.adapter.out.messaging;

import java.util.List;

import org.apache.avro.specific.SpecificRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import com.matcodem.fincore.fraud.domain.event.DomainEvent;
import com.matcodem.fincore.fraud.domain.event.FraudCaseApprovedEvent;
import com.matcodem.fincore.fraud.domain.event.FraudCaseBlockedEvent;
import com.matcodem.fincore.fraud.domain.event.FraudCaseEscalatedEvent;
import com.matcodem.fincore.fraud.domain.event.FraudConfirmedEvent;
import com.matcodem.fincore.fraud.domain.port.out.FraudEventPublisher;
import com.matcodem.fincore.fraud.infrastructure.messaging.avro.AvroFraudEventMapper;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaFraudEventPublisher implements FraudEventPublisher {

	private final KafkaTemplate<String, Object> avroKafkaTemplate;
	private final AvroFraudEventMapper avroFraudEventMapper;
	private final MeterRegistry meterRegistry;

	private static final String TOPIC_PREFIX = "fincore.fraud.";

	@Override
	public void publishAll(List<DomainEvent> events) {
		events.forEach(this::publish);
	}

	private void publish(DomainEvent event) {
		SpecificRecord avroRecord = avroFraudEventMapper.toAvro(event);
		String topic = resolveTopic(event);
		String key = resolveKey(event);

		avroKafkaTemplate.send(topic, key, avroRecord)
				.whenComplete((result, ex) -> {
					if (ex != null) {
						log.error("Failed to publish Avro fraud event {} to {}: {}",
								event.eventType(), topic, ex.getMessage());
						meterRegistry.counter("fraud.publisher.error",
								"eventType", event.eventType()).increment();
					} else {
						log.debug("Published Avro fraud event {} -> {} offset={}",
								event.eventType(), topic,
								result.getRecordMetadata().offset());
						meterRegistry.counter("fraud.publisher.success",
								"eventType", event.eventType()).increment();
					}
				});
	}

	private String resolveTopic(DomainEvent event) {
		// fraud.case.approved -> fincore.fraud.case-approved
		// fraud.confirmed     -> fincore.fraud.confirmed
		return TOPIC_PREFIX + event.eventType().replace("fraud.", "").replace(".", "-");
	}

	private String resolveKey(DomainEvent event) {
		return switch (event) {
			case FraudCaseApprovedEvent e -> e.paymentId();
			case FraudCaseBlockedEvent e -> e.paymentId();
			case FraudCaseEscalatedEvent e -> e.paymentId();
			case FraudConfirmedEvent e -> e.paymentId();
			default -> event.aggregateId();
		};
	}
}