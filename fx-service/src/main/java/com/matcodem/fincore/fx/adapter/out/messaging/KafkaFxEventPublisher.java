package com.matcodem.fincore.fx.adapter.out.messaging;

import java.util.List;
import java.util.Map;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.matcodem.fincore.fx.domain.event.DomainEvent;
import com.matcodem.fincore.fx.domain.event.ExchangeRatePublishedEvent;
import com.matcodem.fincore.fx.domain.event.FxConversionExecutedEvent;
import com.matcodem.fincore.fx.domain.event.FxConversionFailedEvent;
import com.matcodem.fincore.fx.domain.port.out.FxEventPublisher;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaFxEventPublisher implements FxEventPublisher {

	private final KafkaTemplate<String, String> kafkaTemplate;
	private final ObjectMapper objectMapper;

	private static final String TOPIC_PREFIX = "fincore.fx.";

	@Override
	public void publishAll(List<DomainEvent> events) {
		events.forEach(this::publish);
	}

	private void publish(DomainEvent event) {
		String topic = TOPIC_PREFIX + event.eventType().replace("fx.", "").replace(".", "-");

		try {
			Map<String, Object> payload = buildPayload(event);
			String json = objectMapper.writeValueAsString(payload);

			kafkaTemplate.send(topic, event.aggregateId(), json)
					.whenComplete((r, ex) -> {
						if (ex != null) {
							log.error("Failed to publish {} to {}: {}", event.eventType(), topic, ex.getMessage());
						} else {
							log.debug("Published {} → {}", event.eventType(), topic);
						}
					});
		} catch (Exception ex) {
			throw new RuntimeException("Failed to serialize FX event: " + event.eventType(), ex);
		}
	}

	private Map<String, Object> buildPayload(DomainEvent event) {
		return switch (event) {
			case ExchangeRatePublishedEvent e -> Map.of(
					"eventId", e.eventId(), "eventType", e.eventType(),
					"rateId", e.rateId().toString(), "pair", e.pair().getSymbol(),
					"midRate", e.midRate(), "bidRate", e.bidRate(),
					"askRate", e.askRate(), "spreadBps", e.spreadBasisPoints(),
					"occurredAt", e.occurredAt().toString()
			);
			case FxConversionExecutedEvent e -> Map.ofEntries(
					Map.entry("eventId", e.eventId()),
					Map.entry("eventType", e.eventType()),
					Map.entry("conversionId", e.conversionId().toString()),
					Map.entry("paymentId", e.paymentId()),
					Map.entry("accountId", e.accountId()),
					Map.entry("pair", e.pair().getSymbol()),
					Map.entry("sourceAmount", e.sourceAmount()),
					Map.entry("convertedAmount", e.convertedAmount()),
					Map.entry("appliedRate", e.appliedRate()),
					Map.entry("fee", e.fee()),
					Map.entry("occurredAt", e.occurredAt().toString())
			);
			case FxConversionFailedEvent e -> Map.of(
					"eventId", e.eventId(), "eventType", e.eventType(),
					"conversionId", e.conversionId().toString(),
					"paymentId", e.paymentId(), "pair", e.pair().getSymbol(),
					"reason", e.reason(), "occurredAt", e.occurredAt().toString()
			);
			default -> Map.of(
					"eventId", event.eventId(), "eventType", event.eventType(),
					"aggregateId", event.aggregateId(), "occurredAt", event.occurredAt().toString()
			);
		};
	}
}
