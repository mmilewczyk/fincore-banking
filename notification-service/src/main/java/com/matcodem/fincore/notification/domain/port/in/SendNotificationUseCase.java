package com.matcodem.fincore.notification.domain.port.in;

import java.util.List;

import com.matcodem.fincore.notification.domain.model.NotificationPayload;
import com.matcodem.fincore.notification.domain.model.NotificationType;
import com.matcodem.fincore.notification.domain.model.RecipientContact;

/**
 * Primary port - creates and persists Notification aggregates for dispatch.
 * Called by Kafka consumers when a domain event arrives.
 * <p>
 * Creates one Notification per channel per event (fan-out by channel).
 * Returns notification IDs for the caller to log/trace.
 */
public interface SendNotificationUseCase {

	/**
	 * Creates PENDING notifications for all applicable channels.
	 *
	 * @param correlationEventId source domain event ID - used for deduplication
	 * @param recipientContact   contact details (email, phone, FCM token)
	 * @param type               notification type - determines channel routing
	 * @param payload            title/body/templateData for rendering
	 * @return IDs of created notifications (one per channel)
	 */
	List<String> createNotifications(
			String correlationEventId,
			RecipientContact recipientContact,
			NotificationType type,
			NotificationPayload payload
	);
}