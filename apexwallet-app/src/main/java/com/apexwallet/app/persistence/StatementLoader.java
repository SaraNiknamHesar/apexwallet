package com.apexwallet.app.persistence;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Two ways to load a statement. The lazy way issues one query per transaction.
 * The joined way issues one query. {@link JpaBank#statement} uses the joined shape.
 */
@Component
@Profile("postgres")
public class StatementLoader {

    @PersistenceContext
    private EntityManager em;

    @Transactional(readOnly = true)
    public int lazyLoads(List<UUID> transactionIds) {
        int start = SqlCounter.count();
        List<TransactionEntity> rows = em.createQuery(
                        "select t from TransactionEntity t where t.id in :ids", TransactionEntity.class)
                .setParameter("ids", transactionIds)
                .getResultList();
        for (TransactionEntity row : rows) {
            row.getEntries().size();
        }
        return SqlCounter.count() - start;
    }

    @Transactional(readOnly = true)
    public int joinedLoads(List<UUID> transactionIds) {
        int start = SqlCounter.count();
        em.createQuery("""
                        select distinct t from TransactionEntity t
                        left join fetch t.entries
                        where t.id in :ids
                        """, TransactionEntity.class)
                .setParameter("ids", transactionIds)
                .getResultList();
        return SqlCounter.count() - start;
    }
}
