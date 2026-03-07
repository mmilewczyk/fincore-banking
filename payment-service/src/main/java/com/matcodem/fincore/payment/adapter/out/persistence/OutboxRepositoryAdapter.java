package com.matcodem.fincore.payment.adapter.out.persistence;

import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.matcodem.fincore.payment.domain.port.out.OutboxRepository;
import com.matcodem.fincore.payment.domain.model.OutboxMessage;
import com.matcodem.fincore.payment.infrastructure.persistence.entity.OutboxMessageJpaEntity;
import com.matcodem.fincore.payment.infrastructure.persistence.repository.OutboxJpaRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class OutboxRepositoryAdapter implements OutboxRepository {

	private final OutboxJpaRepository outboxJpaRepository;

	@Override
	public void save(OutboxMessage message) {
		outboxJpaRepository.save(toEntity(message));
	}

	@Override
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public List<OutboxMessage> findPendingMessages(int limit) {
		return outboxJpaRepository.findPendingMessagesForUpdate(limit)
				.stream().map(this::toDomain).toList();
	}

	@Override
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void markSent(OutboxMessage message) {
		outboxJpaRepository.findById(message.getId()).ifPresent(entity -> {
			entity.setStatus(OutboxMessage.OutboxStatus.SENT.name());
			entity.setProcessedAt(java.time.Instant.now());
			outboxJpaRepository.save(entity);
		});
	}

	@Override
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void markFailed(OutboxMessage message) {
		message.markFailed();
		outboxJpaRepository.findById(message.getId()).ifPresent(entity -> {
			entity.setStatus(message.getStatus().name());
			entity.setRetryCount(message.getRetryCount());
			outboxJpaRepository.save(entity);
		});
	}

	private OutboxMessageJpaEntity toEntity(OutboxMessage m) {
		var e = new OutboxMessageJpaEntity();
		e.setId(m.getId());
		e.setAggregateId(m.getAggregateId());
		e.setAggregateType(m.getAggregateType());
		e.setEventType(m.getEventType());
		e.setPayload(m.getPayload());
		e.setStatus(m.getStatus().name());
		e.setRetryCount(m.getRetryCount());
		e.setCreatedAt(m.getCreatedAt());
		e.setProcessedAt(m.getProcessedAt());
		return e;
	}

	private OutboxMessage toDomain(OutboxMessageJpaEntity e) {
		return OutboxMessage.reconstitute(
				e.getId(), e.getAggregateId(), e.getAggregateType(),
				e.getEventType(), e.getPayload(),
				OutboxMessage.OutboxStatus.valueOf(e.getStatus()),
				e.getRetryCount(), e.getCreatedAt(), e.getProcessedAt()
		);
	}
}