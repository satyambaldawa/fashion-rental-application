package com.fashionrental.common.numbering;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

// @Repository (not @Component) so Spring translates lock failures raised here into
// PessimisticLockingFailureException, which GlobalExceptionHandler maps to 409.
@Repository
public class DailyDocumentCounter {

    private static final String NEXT_VALUE_SQL = """
            INSERT INTO daily_document_counters (series, counter_date, last_value)
            VALUES (:series, :day, 1)
            ON CONFLICT (series, counter_date)
            DO UPDATE SET last_value = daily_document_counters.last_value + 1
            RETURNING last_value
            """;

    @PersistenceContext
    private EntityManager entityManager;

    // Joins the caller's transaction on purpose: a rollback also rolls back the increment, so a
    // failed checkout never burns a number. The upsert row-locks the counter until commit, which
    // serialises concurrent callers.
    @Transactional(propagation = Propagation.MANDATORY)
    public int nextValue(DocumentSeries series, LocalDate day) {
        Object value = entityManager.createNativeQuery(NEXT_VALUE_SQL)
                .setParameter("series", series.name())
                .setParameter("day", day)
                .getSingleResult();
        return ((Number) value).intValue();
    }
}
