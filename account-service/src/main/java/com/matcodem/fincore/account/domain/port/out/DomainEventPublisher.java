package com.matcodem.fincore.account.domain.port.out;

import java.util.List;

import com.matcodem.fincore.account.domain.event.DomainEvent;

public interface DomainEventPublisher {

	void publish(DomainEvent event);

	void publishAll(List<DomainEvent> events);
}