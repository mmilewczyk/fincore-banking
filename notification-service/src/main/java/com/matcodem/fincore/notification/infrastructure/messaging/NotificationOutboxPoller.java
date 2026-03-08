package com.matcodem.fincore.notification.infrastructure.messaging;

import com.matcodem.fincore.notification.domain.model.Notification;
import com.matcodem.fincore.notification.domain.model.NotificationStatus;
import com.matcodem.fincore.notification.domain.port.in.DispatchNotificationUseCase;
import com.matcodem.fincore.notification.domain.port.out.NotificationRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Polls PENDING and FAILED notifications and dispatches them via DispatchNotificationUseCase.
 *
 * Same pattern as payment-service OutboxPoller:
 * - Fixed delay polling (not cron) - prevents pile-up if dispatch is slow
 * - Batch size limits per-cycle work - prevents memory pressure on large backlogs
 * - Each notification dispatched independently - one failure doesn't block others
 *
 * Why not use Spring's @Async or a thread pool here?
 * Notifications are I/O bound (HTTP to SMTP/FCM/Twilio). Under high volume,
 * consider replacing this with a thread-pool executor or a reactive pipeline.
 * For now, sequential dispatch within the polling batch is simple and correct.
 *
 * Retry backoff:
 * The poller fires every FIXED_DELAY_MS. FAILED notifications are retried on
 * every poll cycle until DEAD_LETTER. For production, add exponential backoff
 * by checking retryCount and skipping notifications that retried too recently
 * (requires a next_retry_at column - not implemented here for simplicity).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationOutboxPoller {

	private final NotificationRepository notificationRepository;
	private final DispatchNotificationUseCase dispatchNotificationUseCase;
	private final MeterRegistry meterRegistry;

	@Value("${notification.poller.batch-size:50}")
	private int batchSize;

	@Scheduled(fixedDelayString = "${notification.poller.fixed-delay-ms:1000}")
	public void poll() {
		List<Notification> pending = notificationRepository
				.findByStatusForDispatch(NotificationStatus.PENDING, batchSize);

		if (pending.isEmpty()) return;

		log.debug("NotificationOutboxPoller: dispatching {} notification(s)", pending.size());
		meterRegistry.counter("notification.poller.batch_size").increment(pending.size());

		for (Notification notification : pending) {
			try {
				dispatchNotificationUseCase.dispatch(notification);
			} catch (Exception ex) {
				// DispatchNotificationService handles all exceptions internally
				// and updates notification status. This catch is a safety net for
				// unexpected unchecked exceptions (NPE, config errors etc.)
				log.error("Unexpected error dispatching notification {}: {}",
						notification.getId(), ex.getMessage(), ex);
			}
		}
	}
}