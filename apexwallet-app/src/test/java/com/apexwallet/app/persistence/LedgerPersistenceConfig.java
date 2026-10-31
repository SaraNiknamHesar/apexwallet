package com.apexwallet.app.persistence;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootConfiguration
@EnableAutoConfiguration
@Profile("ledger-it")
@Import({JpaBank.class, StatementLoader.class})
public class LedgerPersistenceConfig {

    @Bean
    TransactionTemplate transactionTemplate(PlatformTransactionManager manager) {
        return new TransactionTemplate(manager);
    }

    @Bean
    HibernatePropertiesCustomizer sqlCounter() {
        return properties -> properties.put(
                "hibernate.session_factory.statement_inspector",
                SqlCounter.class.getName());
    }
}
