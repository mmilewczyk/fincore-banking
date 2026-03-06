package com.matcodem.fincore.fraud.domain.port.out;

import java.util.List;

import com.matcodem.fincore.fraud.domain.event.DomainEvent;

public interface FraudEventPublisher {
	void publishAll(List<DomainEvent> events);
}
