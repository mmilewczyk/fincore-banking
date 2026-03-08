package com.matcodem.fincore.account.adapter.out.messaging;

import java.util.List;

import org.apache.avro.specific.SpecificRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import com.matcodem.fincore.account.domain.event.DomainEvent;
import com.matcodem.fincore.account.domain.port.out.DomainEventPublisher;
import com.matcodem.fincore.account.infrastructure.messaging.avro.AvroAccountEventMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaDomainEventPublisher implements DomainEventPublisher {

	private final KafkaTemplate<String, Object> avroKafkaTemplate;
	private final AvroAccountEventMapper avroAccountEventMapper;

	private static final String TOPIC_PREFIX = "fincore.accounts.";

	@Override
	public void publish(DomainEvent event) {
		SpecificRecord avroRecord = avroAccountEventMapper.toAvro(event);
		String topic = TOPIC_PREFIX + event.eventType().replace(".", "-");
		String key = event.aggregateId();

		avroKafkaTemplate.send(topic, key, avroRecord)
				.whenComplete((result, ex) -> {
					if (ex != null) {
						log.error("Failed to publish Avro account event {} to {}: {}",
								event.eventType(), topic, ex.getMessage());
					} else {
						log.debug("Published Avro account event {} -> {} offset={}",
								event.eventType(), topic,
								result.getRecordMetadata().offset());
					}
				});
	}

	@Override
	public void publishAll(List<DomainEvent> events) {
		events.forEach(this::publish);
	}
}