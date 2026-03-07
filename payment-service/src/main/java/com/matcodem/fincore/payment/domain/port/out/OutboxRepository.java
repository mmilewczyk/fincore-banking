package com.matcodem.fincore.payment.domain.port.out;

import java.util.List;

import com.matcodem.fincore.payment.domain.model.OutboxMessage;

public interface OutboxRepository {

	void save(OutboxMessage message);

	/**
	 * Fetch pending messages for processing.
	 * Uses SELECT FOR UPDATE SKIP LOCKED for safe concurrent polling.
	 */
	List<OutboxMessage> findPendingMessages(int limit);

	void markSent(OutboxMessage message);

	void markFailed(OutboxMessage message);
}