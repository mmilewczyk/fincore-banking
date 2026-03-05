package com.matcodem.fincore.account.adapter.in.web.dto;

import java.time.Instant;

public record AuditLogResponse(
		String accountId,
		String eventType,
		String performedBy,
		String details,
		Instant occurredAt
) {
}
