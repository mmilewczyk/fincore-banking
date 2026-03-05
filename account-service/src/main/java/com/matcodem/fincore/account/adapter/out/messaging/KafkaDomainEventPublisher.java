package com.matcodem.fincore.account.adapter.out.messaging;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import com.matcodem.fincore.account.domain.event.DomainEvent;
import com.matcodem.fincore.account.domain.port.out.DomainEventPublisher;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Topic naming convention: fincore.accounts.<event-type>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaDomainEventPublisher implements DomainEventPublisher {

	private final KafkaTemplate<String, Object> kafkaTemplate;

	private static final String TOPIC_PREFIX = "fincore.accounts.";

	@Override
	public void publish(DomainEvent event) {
		String topic = TOPIC_PREFIX + event.eventType().replace(".", "-");
		String key = event.aggregateId();

		CompletableFuture<SendResult<String, Object>> future =
				kafkaTemplate.send(topic, key, event);

		future.whenComplete((result, ex) -> {
			if (ex != null) {
				log.error("Failed to publish event {} to topic {}: {}",
						event.eventType(), topic, ex.getMessage());
			} else {
				log.debug("Published event {} to {}:{}", event.eventType(), topic,
						result.getRecordMetadata().offset());
			}
		});
	}

	@Override
	public void publishAll(List<DomainEvent> events) {
		events.forEach(this::publish);
	}
}