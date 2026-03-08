package com.matcodem.fincore.notification.adapter.in.messaging;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import com.matcodem.fincore.notification.domain.model.NotificationType;
import com.matcodem.fincore.notification.domain.model.RecipientContact;
import com.matcodem.fincore.notification.domain.port.in.SendNotificationUseCase;
import com.matcodem.fincore.notification.domain.port.out.UserContactResolver;
import com.matcodem.fincore.notification.domain.service.NotificationPayloadFactory;
import com.matcodem.fincore.payment.avro.PaymentCompletedEvent;
import com.matcodem.fincore.payment.avro.PaymentFailedEvent;
import com.matcodem.fincore.payment.avro.PaymentFraudRejectedEvent;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Listens to payment domain events (Avro) and triggers notification creation.
 * <p>
 * Topics consumed:
 * fincore.payments.payment-completed      -> PAYMENT_COMPLETED
 * fincore.payments.payment-failed         -> PAYMENT_FAILED
 * fincore.payments.payment-fraud-rejected -> PAYMENT_FRAUD_REJECTED
 * <p>
 * One consumer group per topic - each listener is independent:
 * - Independent offset tracking per topic
 * - Failed processing on one topic doesn't block others
 * - concurrency=3 matches Kafka partition count (3 partitions per topic)
 * <p>
 * Manual ACK: only acknowledge after successful notification creation + DB save.
 * If DB save fails, Kafka re-delivers -> idempotency guard in SendNotificationService
 * prevents duplicate notifications.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventKafkaConsumer {

	private final SendNotificationUseCase sendNotificationUseCase;
	private final UserContactResolver userContactResolver;
	private final NotificationPayloadFactory payloadFactory;
	private final MeterRegistry meterRegistry;

	@KafkaListener(
			topics = "fincore.payments.payment-completed",
			groupId = "notification-service-payments",
			containerFactory = "notificationKafkaListenerContainerFactory",
			concurrency = "3"
	)
	public void onPaymentCompleted(ConsumerRecord<String, Object> record, Acknowledgment ack) {
		PaymentCompletedEvent event = (PaymentCompletedEvent) record.value();
		log.info("PaymentCompleted received: paymentId={}", event.getPaymentId());

		try {
			RecipientContact contact = userContactResolver.resolve(event.getInitiatedBy());
			var payload = payloadFactory.forPaymentCompleted(
					event.getPaymentId(),
					event.getAmount(),
					event.getCurrency().name(),
					event.getTargetAccountId()
			);
			sendNotificationUseCase.createNotifications(
					event.getEventId(), contact, NotificationType.PAYMENT_COMPLETED, payload);

			meterRegistry.counter("notification.consumer.payment_completed").increment();
			ack.acknowledge();

		} catch (Exception ex) {
			log.error("Failed to create notifications for PaymentCompleted {}: {}",
					event.getPaymentId(), ex.getMessage(), ex);
			throw new RuntimeException("Notification creation failed", ex);
		}
	}

	@KafkaListener(
			topics = "fincore.payments.payment-failed",
			groupId = "notification-service-payments",
			containerFactory = "notificationKafkaListenerContainerFactory",
			concurrency = "3"
	)
	public void onPaymentFailed(ConsumerRecord<String, Object> record, Acknowledgment ack) {
		PaymentFailedEvent event = (PaymentFailedEvent) record.value();
		log.info("PaymentFailed received: paymentId={}", event.getPaymentId());

		try {
			RecipientContact contact = userContactResolver.resolve(event.getInitiatedBy());
			var payload = payloadFactory.forPaymentFailed(
					event.getPaymentId(),
					event.getAmount(),
					event.getCurrency().name(),
					event.getReason()
			);
			sendNotificationUseCase.createNotifications(
					event.getEventId(), contact, NotificationType.PAYMENT_FAILED, payload);

			meterRegistry.counter("notification.consumer.payment_failed").increment();
			ack.acknowledge();

		} catch (Exception ex) {
			log.error("Failed to create notifications for PaymentFailed {}: {}",
					event.getPaymentId(), ex.getMessage(), ex);
			throw new RuntimeException("Notification creation failed", ex);
		}
	}

	@KafkaListener(
			topics = "fincore.payments.payment-fraud-rejected",
			groupId = "notification-service-payments",
			containerFactory = "notificationKafkaListenerContainerFactory",
			concurrency = "3"
	)
	public void onPaymentFraudRejected(ConsumerRecord<String, Object> record, Acknowledgment ack) {
		PaymentFraudRejectedEvent event = (PaymentFraudRejectedEvent) record.value();
		log.warn("PaymentFraudRejected received: paymentId={}", event.getPaymentId());

		try {
			RecipientContact contact = userContactResolver.resolve(event.getInitiatedBy());
			var payload = payloadFactory.forPaymentFraudRejected(
					event.getPaymentId(),
					event.getAmount(),
					event.getCurrency().name()
			);
			sendNotificationUseCase.createNotifications(
					event.getEventId(), contact, NotificationType.PAYMENT_FRAUD_REJECTED, payload);

			meterRegistry.counter("notification.consumer.payment_fraud_rejected").increment();
			ack.acknowledge();

		} catch (Exception ex) {
			log.error("Failed to create notifications for PaymentFraudRejected {}: {}",
					event.getPaymentId(), ex.getMessage(), ex);
			throw new RuntimeException("Notification creation failed", ex);
		}
	}
}