package com.matcodem.fincore.payment.domain.model;

public enum PaymentStatus {
	PENDING,          // Created, waiting to be processed
	PROCESSING,       // Lock acquired, debit/credit in progress
	COMPLETED,        // Both accounts updated, events published
	FAILED,           // Processing failed (insufficient funds, account frozen, etc.)
	CANCELLED,        // Canceled before processing started
	REJECTED_FRAUD    // Rejected by fraud detection service
}