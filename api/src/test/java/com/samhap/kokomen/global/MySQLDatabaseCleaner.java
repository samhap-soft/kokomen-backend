package com.samhap.kokomen.global;

import jakarta.persistence.EntityManager;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class MySQLDatabaseCleaner implements BeforeEachCallback {

    private String databaseName;

    @Override
    public void beforeEach(ExtensionContext extensionContext) throws Exception {
        ApplicationContext context = SpringExtension.getApplicationContext(extensionContext);

        if (databaseName == null) {
            extractDatabaseName(context);
        }

        cleanup(context);
    }

    private void extractDatabaseName(ApplicationContext context) {
        DataSource dataSource = context.getBean(DataSource.class);
        try (Connection conn = dataSource.getConnection()) {
            databaseName = conn.getCatalog();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to extract database name", e);
        }
    }

    private void cleanup(ApplicationContext context) {
        EntityManager em = context.getBean(EntityManager.class);
        TransactionTemplate transactionTemplate = context.getBean(TransactionTemplate.class);

        transactionTemplate.execute(action -> {
            em.clear();
            truncateTables(em);
            return null;
        });
    }

    private void truncateTables(EntityManager em) {
        em.createNativeQuery("SET FOREIGN_KEY_CHECKS = 0").executeUpdate();
        for (String tableName : findTableNames(em)) {
            if (tableName.equals("flyway_schema_history")) {
                continue;
            }
            em.createNativeQuery("TRUNCATE TABLE %s".formatted(tableName)).executeUpdate();
        }
        em.createNativeQuery("SET FOREIGN_KEY_CHECKS = 1").executeUpdate();
    }

    @SuppressWarnings("unchecked")
    private List<String> findTableNames(EntityManager em) {
        String tableNameSelectQuery = String.format("""
                SELECT TABLE_NAME
                FROM INFORMATION_SCHEMA.TABLES
                WHERE TABLE_SCHEMA = '%s'
                AND TABLE_TYPE = 'BASE TABLE'
                """, databaseName);

        return em.createNativeQuery(tableNameSelectQuery)
                .getResultList();
    }
}
