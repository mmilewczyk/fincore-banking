package com.matcodem.fincore.account.domain.model;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Value Object — International Bank Account Number.
 * Validates format and check digits on construction.
 */
public final class IBAN {

	// Simplified IBAN pattern — full validation via check digits below
	private static final Pattern IBAN_PATTERN = Pattern.compile("[A-Z]{2}[0-9]{2}[A-Z0-9]{1,30}");
	private static final int MOD = 97;

	private final String value;

	private IBAN(String value) {
		Objects.requireNonNull(value, "IBAN cannot be null");
		String normalized = value.replaceAll("\\s+", "").toUpperCase();
		if (!IBAN_PATTERN.matcher(normalized).matches()) {
			throw new InvalidIBANException("Invalid IBAN format: " + value);
		}
		if (!isValidChecksum(normalized)) {
			throw new InvalidIBANException("Invalid IBAN checksum: " + value);
		}
		this.value = normalized;
	}

	public static IBAN of(String value) {
		return new IBAN(value);
	}

	/**
	 * Generates a test IBAN for Polish bank accounts.
	 * Format: PL + 2 check digits + 8-digit bank code + 16-digit account
	 */
	public static IBAN generatePolish(String bankCode, String accountNumber) {
		String bban = bankCode + accountNumber;
		String ibanBase = bban + "PL00";
		String numericIban = toNumeric(ibanBase);
		int checkDigits = 98 - (int) (new java.math.BigInteger(numericIban).mod(java.math.BigInteger.valueOf(MOD))).longValue();
		return new IBAN("PL%02d%s".formatted(checkDigits, bban));
	}

	public String getValue() {
		return value;
	}

	public String getFormatted() {
		// Groups of 4 chars: PL12 1234 5678 ...
		return value.replaceAll("(.{4})", "$1 ").trim();
	}

	public String getCountryCode() {
		return value.substring(0, 2);
	}

	private static boolean isValidChecksum(String iban) {
		// Move first 4 chars to end, convert to numeric, check mod 97 == 1
		String rearranged = iban.substring(4) + iban.substring(0, 4);
		String numeric = toNumeric(rearranged);
		return new java.math.BigInteger(numeric).mod(java.math.BigInteger.valueOf(MOD)).intValue() == 1;
	}

	private static String toNumeric(String s) {
		StringBuilder sb = new StringBuilder();
		for (char c : s.toCharArray()) {
			if (Character.isLetter(c)) {
				sb.append(c - 'A' + 10);
			} else {
				sb.append(c);
			}
		}
		return sb.toString();
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof IBAN iban)) return false;
		return Objects.equals(value, iban.value);
	}

	@Override
	public int hashCode() {
		return Objects.hash(value);
	}

	@Override
	public String toString() {
		return getFormatted();
	}
}