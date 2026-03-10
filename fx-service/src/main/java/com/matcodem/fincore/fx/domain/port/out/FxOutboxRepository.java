package com.matcodem.fincore.fx.domain.port.out;

import java.util.List;

import com.matcodem.fincore.fx.domain.event.DomainEvent;

public interface FxOutboxRepository {

	void append(List<DomainEvent> events, String aggregateType);
}