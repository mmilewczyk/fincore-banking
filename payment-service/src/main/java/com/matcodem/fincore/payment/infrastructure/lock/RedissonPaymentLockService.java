package com.matcodem.fincore.payment.infrastructure.lock;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import com.matcodem.fincore.payment.domain.port.out.PaymentLockService;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Distributed Lock implementation using Redisson.
 * <p>
 * Key design decisions:
 * <p>
 * 1. WATCHDOG — we use lockInterruptibly() WITHOUT leaseTime.
 * This activates Redisson's watchdog which auto-renews the lock every
 * lockWatchdogTimeout/3 seconds (default: every 10s for 30s TTL).
 * → No risk of lock expiring during long operations.
 * <p>
 * 2. ORDERED LOCKING — always acquire locks in alphabetical order of account IDs.
 * → Prevents deadlocks when two payments involve the same two accounts.
 * Example: Payment A: [acc-1 → acc-2], Payment B: [acc-2 → acc-1]
 * Both will try to lock acc-1 first → safe, no deadlock.
 * <p>
 * 3. MULTI-LOCK — uses RedissonMultiLock to acquire both locks atomically.
 * → Either both are acquired or neither is.
 * <p>
 * 4. GUARANTEED RELEASE — unlock always in finally block.
 * → Watchdog stops automatically when unlock() is called.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedissonPaymentLockService implements PaymentLockService {

	private final RedissonClient redissonClient;
	private final MeterRegistry meterRegistry;

	private static final String LOCK_PREFIX = "fincore:payment:account:";
	private static final long WAIT_TIMEOUT_SECONDS = 10L;

	@Override
	public void acquireLock(String sourceAccountId, String targetAccountId) {
		List<RLock> locks = getOrderedLocks(sourceAccountId, targetAccountId);
		RLock multiLock = redissonClient.getMultiLock(locks.toArray(new RLock[0]));

		try {
			boolean acquired = multiLock.tryLock(WAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
			// Note: no leaseTime → watchdog activated
			if (!acquired) {
				meterRegistry.counter("payment.lock.timeout").increment();
				throw new LockAcquisitionException(
						"Could not acquire lock for accounts [%s, %s] within %ds"
								.formatted(sourceAccountId, targetAccountId, WAIT_TIMEOUT_SECONDS)
				);
			}
			meterRegistry.counter("payment.lock.acquired").increment();
			log.debug("Acquired lock for accounts: [{}, {}]", sourceAccountId, targetAccountId);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new LockAcquisitionException("Interrupted while waiting for lock", e);
		}
	}

	@Override
	public void releaseLock(String sourceAccountId, String targetAccountId) {
		List<RLock> locks = getOrderedLocks(sourceAccountId, targetAccountId);
		RLock multiLock = redissonClient.getMultiLock(locks.toArray(new RLock[0]));

		try {
			if (multiLock.isHeldByCurrentThread()) {
				multiLock.unlock();
				meterRegistry.counter("payment.lock.released").increment();
				log.debug("Released lock for accounts: [{}, {}]", sourceAccountId, targetAccountId);
			}
		} catch (Exception e) {
			// Lock may have expired — log but don't throw (already in finally block)
			log.warn("Error releasing lock for accounts [{}, {}]: {}",
					sourceAccountId, targetAccountId, e.getMessage());
		}
	}

	@Override
	public void executeWithLock(String sourceAccountId, String targetAccountId, Runnable action) {
		Timer.Sample sample = Timer.start(meterRegistry);

		List<RLock> locks = getOrderedLocks(sourceAccountId, targetAccountId);
		RLock multiLock = redissonClient.getMultiLock(locks.toArray(new RLock[0]));

		try {
			boolean acquired = multiLock.tryLock(WAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
			// No leaseTime = watchdog auto-renews the lock
			if (!acquired) {
				meterRegistry.counter("payment.lock.timeout").increment();
				throw new LockAcquisitionException(
						"Could not acquire lock for accounts [%s, %s] within %ds"
								.formatted(sourceAccountId, targetAccountId, WAIT_TIMEOUT_SECONDS)
				);
			}

			log.debug("Lock acquired for accounts [{}, {}], executing action",
					sourceAccountId, targetAccountId);

			try {
				action.run();
			} finally {
				// Always release — watchdog stops here
				safeUnlock(multiLock, sourceAccountId, targetAccountId);
			}

		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new LockAcquisitionException("Interrupted while waiting for lock", e);
		} finally {
			sample.stop(meterRegistry.timer("payment.lock.duration"));
		}
	}

	/**
	 * Always sort account IDs alphabetically to prevent deadlocks.
	 * Both [A→B] and [B→A] payments will try to lock A first.
	 */
	private List<RLock> getOrderedLocks(String sourceAccountId, String targetAccountId) {
		String[] sortedIds = new String[]{sourceAccountId, targetAccountId};
		Arrays.sort(sortedIds);

		return Arrays.stream(sortedIds)
				.map(id -> redissonClient.getLock(LOCK_PREFIX + id))
				.toList();
	}

	private void safeUnlock(RLock lock, String sourceId, String targetId) {
		try {
			if (lock.isHeldByCurrentThread()) {
				lock.unlock();
				meterRegistry.counter("payment.lock.released").increment();
				log.debug("Lock released for accounts [{}, {}]", sourceId, targetId);
			}
		} catch (Exception e) {
			log.warn("Error releasing lock [{}, {}]: {}", sourceId, targetId, e.getMessage());
		}
	}
}
