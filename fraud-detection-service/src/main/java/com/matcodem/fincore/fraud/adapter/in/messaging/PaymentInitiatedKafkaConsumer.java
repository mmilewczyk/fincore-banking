package com.matcodem.fincore.fraud.adapter.in.messaging;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import com.matcodem.fincore.fraud.avro.PaymentInitiatedEvent;
import com.matcodem.fincore.fraud.domain.model.FraudCase;
import com.matcodem.fincore.fraud.domain.model.PaymentContext;
import com.matcodem.fincore.fraud.domain.port.in.AnalysePaymentUseCase;
import com.matcodem.fincore.fraud.infrastructure.messaging.avro.AvroFraudEventMapper;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentInitiatedKafkaConsumer {

	private final AnalysePaymentUseCase analysePaymentUseCase;
	private final AvroFraudEventMapper avroFraudEventMapper;
	private final MeterRegistry meterRegistry;

	@KafkaListener(
			topics = "fincore.payments.payment-initiated",
			groupId = "fraud-detection-service",
			containerFactory = "fraudKafkaListenerContainerFactory",
			concurrency = "3"
	)
	public void onPaymentInitiated(ConsumerRecord<String, Object> record, Acknowledgment ack) {
		PaymentInitiatedEvent avroEvent = (PaymentInitiatedEvent) record.value();
		String paymentId = avroEvent.getPaymentId();

		log.info("Received PaymentInitiatedEvent (Avro): paymentId={}, amount={} {}, partition={}, offset={}",
				paymentId, avroEvent.getAmount(), avroEvent.getCurrency(),
				record.partition(), record.offset());

		try {
			// Type-safe mapping - replaces old Map<String, Object> deserialization
			PaymentContext context = avroFraudEventMapper.toPaymentContext(avroEvent);
			FraudCase result = analysePaymentUseCase.analyse(context);

			log.info("Fraud analysis completed: paymentId={}, outcome={}, score={}",
					paymentId, result.getStatus(), result.getCompositeScore());

			meterRegistry.counter("fraud.consumer.success",
					"paymentType", avroEvent.getPaymentType().name()).increment();
			ack.acknowledge();

		} catch (ClassCastException ex) {
			// Wrong Avro type on this topic - schema mismatch between producer and consumer
			log.error("SCHEMA MISMATCH: Expected PaymentInitiatedEvent but got {} on topic {}, offset {}",
					record.value().getClass().getSimpleName(), record.topic(), record.offset());
			meterRegistry.counter("fraud.consumer.schema_mismatch").increment();
			throw new RuntimeException("Schema mismatch - message routed to DLT", ex);

		} catch (Exception ex) {
			log.error("Fraud analysis failed for paymentId={}: {}", paymentId, ex.getMessage(), ex);
			meterRegistry.counter("fraud.consumer.error",
					"error_type", ex.getClass().getSimpleName()).increment();
			// No ack - Kafka retries, then DLT
			throw new RuntimeException("Fraud analysis failed for payment: " + paymentId, ex);
		}
	}

	/**
	 * Dead letter handler - payments that failed all retries.
	 * Avro message may be undeserializable here (byte[] from ErrorHandlingDeserializer).
	 */
	@KafkaListener(
			topics = "fincore.payments.payment-initiated.DLT",
			groupId = "fraud-detection-dlq-handler"
	)
	public void onDeadLetterMessage(ConsumerRecord<String, ?> record) {
		log.error("DEAD LETTER - fraud analysis permanently failed: key={}, partition={}, offset={} - MANUAL INVESTIGATION REQUIRED",
				record.key(), record.partition(), record.offset());
		meterRegistry.counter("fraud.consumer.dead_letter").increment();
		// TODO: persist to dead_letter_cases table + PagerDuty alert
	}
}