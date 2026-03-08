package com.matcodem.fincore.notification.domain.model;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * Template data for rendering notification content.
 * <p>
 * title + body: used for Push and SMS (plain text, max ~160 chars for SMS).
 * templateData: key-value pairs injected into Thymeleaf email template.
 * e.g. {"amount": "100.00", "currency": "EUR", "targetAccount": "PL61..."}
 * <p>
 * Not a generic Map<String, Object> - keys are explicit strings, no nested objects.
 * This keeps templates simple and prevents injection of arbitrary HTML in emails.
 */
public record NotificationPayload(
		String title,
		String body,
		Map<String, String> templateData
) {
	public NotificationPayload {
		Objects.requireNonNull(title, "title required");
		Objects.requireNonNull(body, "body required");
		templateData = templateData != null
				? Collections.unmodifiableMap(templateData)
				: Collections.emptyMap();
	}

	public static NotificationPayload of(String title, String body, Map<String, String> data) {
		return new NotificationPayload(title, body, data);
	}
}