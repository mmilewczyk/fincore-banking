package com.matcodem.fincore.notification.adapter.out.persistence;

import org.springframework.stereotype.Component;

import com.matcodem.fincore.notification.domain.model.Notification;
import com.matcodem.fincore.notification.domain.model.NotificationChannel;
import com.matcodem.fincore.notification.domain.model.NotificationId;
import com.matcodem.fincore.notification.domain.model.NotificationPayload;
import com.matcodem.fincore.notification.domain.model.NotificationStatus;
import com.matcodem.fincore.notification.domain.model.NotificationType;
import com.matcodem.fincore.notification.domain.model.RecipientContact;
import com.matcodem.fincore.notification.infrastructure.persistence.entity.NotificationJpaEntity;

@Component
public class NotificationMapper {

	public NotificationJpaEntity toEntity(Notification n) {
		var e = new NotificationJpaEntity();
		e.setId(n.getId().value());
		e.setCorrelationEventId(n.getCorrelationEventId());
		e.setRecipientUserId(n.getRecipientUserId());
		e.setType(n.getType().name());
		e.setChannel(n.getChannel().name());
		e.setStatus(n.getStatus().name());
		e.setRecipientEmail(n.getContact().email());
		e.setRecipientPhone(n.getContact().phoneNumber());
		e.setRecipientFcmToken(n.getContact().fcmToken());
		e.setTitle(n.getPayload().title());
		e.setBody(n.getPayload().body());
		e.setTemplateData(n.getPayload().templateData());
		e.setFailureReason(n.getFailureReason());
		e.setRetryCount(n.getRetryCount());
		e.setCreatedAt(n.getCreatedAt());
		e.setUpdatedAt(n.getUpdatedAt());
		return e;
	}

	public Notification toDomain(NotificationJpaEntity e) {
		RecipientContact contact = new RecipientContact(
				e.getRecipientUserId(), e.getRecipientEmail(),
				e.getRecipientPhone(), e.getRecipientFcmToken()
		);
		NotificationPayload payload = NotificationPayload.of(
				e.getTitle(), e.getBody(), e.getTemplateData()
		);
		return Notification.reconstitute(
				NotificationId.of(e.getId()),
				e.getCorrelationEventId(),
				e.getRecipientUserId(),
				NotificationType.valueOf(e.getType()),
				NotificationChannel.valueOf(e.getChannel()),
				contact,
				payload,
				NotificationStatus.valueOf(e.getStatus()),
				e.getFailureReason(),
				e.getRetryCount(),
				e.getCreatedAt(),
				e.getUpdatedAt()
		);
	}
}
