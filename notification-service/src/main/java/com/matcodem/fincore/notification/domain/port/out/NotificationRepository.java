package com.matcodem.fincore.notification.domain.port.out;

import java.util.List;
import java.util.Optional;

import com.matcodem.fincore.notification.domain.model.Notification;
import com.matcodem.fincore.notification.domain.model.NotificationId;
import com.matcodem.fincore.notification.domain.model.NotificationStatus;

public interface NotificationRepository {
	void save(Notification notification);

	void saveAll(List<Notification> notifications);

	Optional<Notification> findById(NotificationId id);

	boolean existsByCorrelationEventIdAndChannel(String correlationEventId,
	                                             com.matcodem.fincore.notification.domain.model.NotificationChannel channel);

	List<Notification> findByStatusForDispatch(NotificationStatus status, int limit);
}
