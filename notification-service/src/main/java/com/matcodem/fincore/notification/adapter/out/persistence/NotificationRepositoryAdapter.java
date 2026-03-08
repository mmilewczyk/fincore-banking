package com.matcodem.fincore.notification.adapter.out.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.matcodem.fincore.notification.domain.model.Notification;
import com.matcodem.fincore.notification.domain.model.NotificationChannel;
import com.matcodem.fincore.notification.domain.model.NotificationId;
import com.matcodem.fincore.notification.domain.model.NotificationStatus;
import com.matcodem.fincore.notification.domain.port.out.NotificationRepository;
import com.matcodem.fincore.notification.infrastructure.persistence.repository.NotificationJpaRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class NotificationRepositoryAdapter implements NotificationRepository {

	private final NotificationJpaRepository notificationJpaRepository;
	private final NotificationMapper mapper;

	@Override
	public void save(Notification notification) {
		notificationJpaRepository.save(mapper.toEntity(notification));
	}

	@Override
	public void saveAll(List<Notification> notifications) {
		notificationJpaRepository.saveAll(notifications.stream().map(mapper::toEntity).toList());
	}

	@Override
	public Optional<Notification> findById(NotificationId id) {
		return notificationJpaRepository.findById(id.value()).map(mapper::toDomain);
	}

	@Override
	public boolean existsByCorrelationEventIdAndChannel(String correlationEventId,
	                                                    NotificationChannel channel) {
		return notificationJpaRepository.existsByCorrelationEventIdAndChannel(correlationEventId, channel.name());
	}

	@Override
	public List<Notification> findByStatusForDispatch(NotificationStatus status, int limit) {
		return notificationJpaRepository.findPendingOrFailed(limit).stream()
				.map(mapper::toDomain)
				.toList();
	}
}
