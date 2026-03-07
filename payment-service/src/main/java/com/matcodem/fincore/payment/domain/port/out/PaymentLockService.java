package com.matcodem.fincore.payment.domain.port.out;

/**
 * Implementation uses Redisson with watchdog (auto-renewal).
 */
public interface PaymentLockService {

	/**
	 * Acquires a distributed lock for the given payment.
	 * Uses account IDs as lock keys to prevent concurrent operations
	 * on the same account across different payments.
	 *
	 * @throws LockAcquisitionException if lock cannot be acquired within timeout
	 */
	void acquireLock(String sourceAccountId, String targetAccountId);

	void releaseLock(String sourceAccountId, String targetAccountId);

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