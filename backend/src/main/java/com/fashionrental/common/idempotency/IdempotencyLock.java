package com.fashionrental.common.idempotency;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

// @Repository (not @Component) so Spring translates lock failures raised here into
// PessimisticLockingFailureException, which GlobalExceptionHandler maps to 409.
@Repository
public class IdempotencyLock {

    // Dedicated first key of the two-int advisory lock form, so these locks can never collide
    // with advisory locks taken for another purpose.
    private static final int IDEMPOTENCY_LOCK_NAMESPACE = 189_001;

    private static final String ACQUIRE_SQL =
            "SELECT 1 FROM pg_advisory_xact_lock(:namespace, :keyHash)";

    @PersistenceContext
    private EntityManager entityManager;

    // Blocks until any other transaction holding the same key commits or rolls back, so a
    // duplicate request that arrives mid-flight waits and then replays instead of racing the
    // original into a unique-index violation. Released automatically at transaction end.
    // Two different keys may fold to the same hash; that only makes them wait for each other.
    @Transactional(propagation = Propagation.MANDATORY)
    public void lock(UUID key) {
        entityManager.createNativeQuery(ACQUIRE_SQL)
                .setParameter("namespace", IDEMPOTENCY_LOCK_NAMESPACE)
                .setParameter("keyHash", foldToInt(key))
                .getSingleResult();
    }

    private static int foldToInt(UUID key) {
        long mixed = key.getMostSignificantBits() ^ key.getLeastSignificantBits();
        return (int) (mixed ^ (mixed >>> 32));
    }
}
