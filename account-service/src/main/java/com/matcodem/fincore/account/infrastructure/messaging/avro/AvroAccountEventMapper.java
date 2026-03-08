package com.matcodem.fincore.account.infrastructure.messaging.avro;

import org.apache.avro.specific.SpecificRecord;
import org.springframework.stereotype.Component;

import com.matcodem.fincore.account.domain.event.AccountCreatedEvent;
import com.matcodem.fincore.account.domain.event.AccountCreditedEvent;
import com.matcodem.fincore.account.domain.event.AccountDebitedEvent;
import com.matcodem.fincore.account.domain.event.AccountFrozenEvent;
import com.matcodem.fincore.account.domain.event.DomainEvent;

@Component
public class AvroAccountEventMapper {

	public SpecificRecord toAvro(DomainEvent event) {
		return switch (event) {
			case AccountCreatedEvent e -> toAvro(e);
			case AccountDebitedEvent e -> toAvro(e);
			case AccountCreditedEvent e -> toAvro(e);
			case AccountFrozenEvent e -> toAvro(e);
			default -> throw new IllegalStateException(
					"No Avro mapping for account event type: " + event.eventType());
		};
	}

	public com.matcodem.fincore.account.avro.AccountCreatedEvent toAvro(AccountCreatedEvent e) {
		return com.matcodem.fincore.account.avro.AccountCreatedEvent.newBuilder()
				.setEventId(e.eventId().toString())
				.setOccurredAt(e.occurredAt())
				.setAccountId(e.accountId().toString())
				.setOwnerId(e.ownerId())
				.setIban(e.iban().getValue())
				.setCurrency(com.matcodem.fincore.account.avro.AccountCurrency.valueOf(e.currency().getCode()))
				.setSchemaVersion(1)
				.build();
	}

	public com.matcodem.fincore.account.avro.AccountDebitedEvent toAvro(AccountDebitedEvent e) {
		return com.matcodem.fincore.account.avro.AccountDebitedEvent.newBuilder()
				.setEventId(e.eventId().toString())
				.setOccurredAt(e.occurredAt())
				.setAccountId(e.accountId().toString())
				.setAmount(e.amount().getAmount())
				.setCurrency(com.matcodem.fincore.account.avro.AccountCurrency.valueOf(e.amount().getCurrency().getCode()))
				.setBalanceAfter(e.balanceAfter().getAmount())
				.setReference(e.reference())
				.setSchemaVersion(1)
				.build();
	}

	public com.matcodem.fincore.account.avro.AccountCreditedEvent toAvro(AccountCreditedEvent e) {
		return com.matcodem.fincore.account.avro.AccountCreditedEvent.newBuilder()
				.setEventId(e.eventId().toString())
				.setOccurredAt(e.occurredAt())
				.setAccountId(e.accountId().toString())
				.setAmount(e.amount().getAmount())
				.setCurrency(com.matcodem.fincore.account.avro.AccountCurrency.valueOf(e.amount().getCurrency().getCode()))
				.setBalanceAfter(e.balanceAfter().getAmount())
				.setReference(e.reference())
				.setSchemaVersion(1)
				.build();
	}

	public com.matcodem.fincore.account.avro.AccountFrozenEvent toAvro(AccountFrozenEvent e) {
		return com.matcodem.fincore.account.avro.AccountFrozenEvent.newBuilder()
				.setEventId(e.eventId().toString())
				.setOccurredAt(e.occurredAt())
				.setAccountId(e.accountId().toString())
				.setReason(e.reason())
				.setFrozenBy(null)  // automated freeze — no human actor
				.setSchemaVersion(1)
				.build();
	}
}