package com.matcodem.fincore.payment.domain.port.out;

/**
 * Implementation uses Redisson with watchdog (auto-renewal).
 */
public interface PaymentLockService {
	/**
	 * Execute action with lock — template method pattern.
	 * Handles acquisition, execution, and guaranteed release.
	 */
	void executeWithLock(String sourceAccountId, String targetAccountId, Runnable action);

	class LockAcquisitionException extends RuntimeException {
		public LockAcquisitionException(String message) {
			super(message);
		}

		public LockAcquisitionException(String message, Throwable cause) {
			super(message, cause);
		}
	}
}