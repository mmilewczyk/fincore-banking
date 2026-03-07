package com.matcodem.fincore.fx.domain.port.out;

import java.util.List;

import com.matcodem.fincore.fx.domain.event.DomainEvent;

public interface FxEventPublisher {
	void publishAll(List<DomainEvent> events);
}
