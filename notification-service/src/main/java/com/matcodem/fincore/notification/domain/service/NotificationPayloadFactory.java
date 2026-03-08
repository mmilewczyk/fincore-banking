package com.matcodem.fincore.notification.domain.service;

import java.math.BigDecimal;
import java.util.Map;

import com.matcodem.fincore.notification.domain.model.NotificationPayload;

/**
 * Pure domain service - builds NotificationPayload from raw event data.
 * <p>
 * All strings are extracted and sanitized here before being passed to
 * template rendering. This is the single place that knows the data shape
 * for each event type.
 * <p>
 * templateData keys must match Thymeleaf template variable names exactly:
 * ${amount}, ${currency}, ${paymentId}, ${accountId} etc.
 */
public class NotificationPayloadFactory {

	public NotificationPayload forPaymentCompleted(String paymentId,
	                                               BigDecimal amount, String currency, String targetAccountId) {
		String amountStr = formatAmount(amount, currency);
		return NotificationPayload.of(
				"Payment Completed",
				"Your payment of %s has been completed successfully.".formatted(amountStr),
				Map.of(
						"paymentId", paymentId,
						"amount", amount.toPlainString(),
						"currency", currency,
						"targetAccount", maskAccount(targetAccountId),
						"formattedAmount", amountStr
				)
		);
	}

	public NotificationPayload forPaymentFailed(String paymentId,
	                                            BigDecimal amount, String currency, String reason) {
		String amountStr = formatAmount(amount, currency);
		return NotificationPayload.of(
				"Payment Failed",
				"Your payment of %s could not be processed. Reason: %s".formatted(amountStr, reason),
				Map.of(
						"paymentId", paymentId,
						"amount", amount.toPlainString(),
						"currency", currency,
						"reason", reason,
						"formattedAmount", amountStr
				)
		);
	}

	public NotificationPayload forPaymentFraudRejected(String paymentId,
	                                                   BigDecimal amount, String currency) {
		String amountStr = formatAmount(amount, currency);
		return NotificationPayload.of(
				"Payment Blocked - Security Alert",
				"A payment of %s was blocked due to suspicious activity. If this was not you, contact support immediately.".formatted(amountStr),
				Map.of(
						"paymentId", paymentId,
						"amount", amount.toPlainString(),
						"currency", currency,
						"formattedAmount", amountStr
				)
		);
	}

	public NotificationPayload forAccountDebited(String accountId,
	                                             BigDecimal amount, String currency, BigDecimal balanceAfter) {
		String amountStr = formatAmount(amount, currency);
		return NotificationPayload.of(
				"Account Debited",
				"%s debited from your account. Balance: %s".formatted(amountStr, formatAmount(balanceAfter, currency)),
				Map.of(
						"accountId", maskAccount(accountId),
						"amount", amount.toPlainString(),
						"currency", currency,
						"balanceAfter", balanceAfter.toPlainString(),
						"formattedAmount", amountStr
				)
		);
	}

	public NotificationPayload forAccountCredited(String accountId,
	                                              BigDecimal amount, String currency, BigDecimal balanceAfter) {
		String amountStr = formatAmount(amount, currency);
		return NotificationPayload.of(
				"Account Credited",
				"%s credited to your account. Balance: %s".formatted(amountStr, formatAmount(balanceAfter, currency)),
				Map.of(
						"accountId", maskAccount(accountId),
						"amount", amount.toPlainString(),
						"currency", currency,
						"balanceAfter", balanceAfter.toPlainString(),
						"formattedAmount", amountStr
				)
		);
	}

	public NotificationPayload forAccountFrozen(String accountId, String reason) {
		return NotificationPayload.of(
				"Account Frozen - Immediate Action Required",
				"Your account has been frozen. Reason: %s. Contact support immediately.".formatted(reason),
				Map.of(
						"accountId", maskAccount(accountId),
						"reason", reason
				)
		);
	}

	private String formatAmount(BigDecimal amount, String currency) {
		return "%s %s".formatted(amount.toPlainString(), currency);
	}

	/**
	 * Masks account ID / IBAN for display in notifications.
	 * Shows first 4 + last 4 characters only.
	 * e.g. PL61109010140000071219812874 -> PL61...2874
	 */
	private String maskAccount(String accountId) {
		if (accountId == null || accountId.length() <= 8) return "****";
		return accountId.substring(0, 4) + "..." + accountId.substring(accountId.length() - 4);
	}
}
