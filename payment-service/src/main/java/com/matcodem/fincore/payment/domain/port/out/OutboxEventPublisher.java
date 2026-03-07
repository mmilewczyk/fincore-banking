package com.matcodem.fincore.payment.domain.port.out;

import com.matcodem.fincore.payment.domain.event.DomainEvent;

public interface OutboxEventPublisher {
	void publish(DomainEvent event, String aggregateType);
}
