package com.matcodem.fincore.fx.adapter.out.persistence;

import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.apache.avro.io.EncoderFactory;
import org.apache.avro.io.JsonEncoder;
import org.apache.avro.specific.SpecificDatumWriter;
import org.apache.avro.specific.SpecificRecord;
import org.springframework.stereotype.Component;

import com.matcodem.fincore.fx.domain.event.DomainEvent;
import com.matcodem.fincore.fx.domain.port.out.FxOutboxRepository;
import com.matcodem.fincore.fx.infrastructure.messaging.avro.AvroFxEventMapper;
import com.matcodem.fincore.fx.infrastructure.persistence.entity.FxOutboxMessageJpaEntity;
import com.matcodem.fincore.fx.infrastructure.persistence.repository.FxOutboxJpaRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class FxOutboxRepositoryAdapter implements FxOutboxRepository {

	private final FxOutboxJpaRepository jpaRepository;
	private final AvroFxEventMapper avroMapper;

	@Override
	public void append(List<DomainEvent> events, String aggregateType) {
		events.forEach(event -> appendOne(event, aggregateType));
	}

	private void appendOne(DomainEvent event, String aggregateType) {
		try {
			SpecificRecord avroRecord = avroMapper.toAvro(event);
			String payload = toAvroJson(avroRecord);

			FxOutboxMessageJpaEntity entity = new FxOutboxMessageJpaEntity();
			entity.setId(UUID.randomUUID());
			entity.setAggregateId(event.aggregateId());
			entity.setAggregateType(aggregateType);
			entity.setEventType(event.eventType());
			entity.setPayload(payload);
			entity.setStatus(FxOutboxMessageJpaEntity.Status.PENDING);
			entity.setRetryCount(0);
			entity.setCreatedAt(Instant.now());

			jpaRepository.save(entity);

		} catch (Exception ex) {
			// Avro serialization failure is a programming error - fail fast so the
			// outer @Transactional rolls back both fx_conversion and outbox writes.
			throw new IllegalStateException(
					"Failed to serialize event %s to outbox".formatted(event.eventType()), ex);
		}
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private String toAvroJson(SpecificRecord record) throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		JsonEncoder encoder = EncoderFactory.get().jsonEncoder(record.getSchema(), out);
		new SpecificDatumWriter(record.getSchema()).write(record, encoder);
		encoder.flush();
		return out.toString();
	}
}