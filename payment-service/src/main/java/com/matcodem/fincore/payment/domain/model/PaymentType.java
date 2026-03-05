package com.matcodem.fincore.payment.domain.model;

public enum PaymentType {
	INTERNAL_TRANSFER,   // Between accounts within FinCore
	EXTERNAL_TRANSFER,   // To external bank (SEPA, SWIFT)
	BILL_PAYMENT,        // Utility/bill payment
	FX_CONVERSION        // Currency exchange (uses FX service)
}