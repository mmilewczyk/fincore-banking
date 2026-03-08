package com.matcodem.fincore.notification.domain.port.in;

import com.matcodem.fincore.notification.domain.model.Notification;

/**
 * Called by the outbox poller to dispatch a single PENDING/FAILED notification.
 * Delegates to the correct channel sender (email/push/SMS) and updates status.
 */
public interface DispatchNotificationUseCase {
	void dispatch(Notification notification);
}