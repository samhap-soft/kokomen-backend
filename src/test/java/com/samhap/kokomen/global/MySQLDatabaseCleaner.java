package com.samhap.kokomen.global;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.metamodel.EntityType;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class MySQLDatabaseCleaner {

    public static final String CAMEL_CASE = "([a-z])([A-Z])";
    public static final String SNAKE_CASE = "$1_$2";

    @PersistenceContext
    private EntityManager entityManager;

    private List<String> tableNames;

    @PostConstruct
    public void findTableNames() {
        tableNames = entityManager.getMetamodel().getEntities().stream()
                .filter(e -> e.getJavaType().getAnnotation(Entity.class) != null)
                .map(MySQLDatabaseCleaner::convertCamelToSnake)
                .toList();
    }

    private static String convertCamelToSnake(final EntityType<?> e) {
        return e.getName()
                .replaceAll(CAMEL_CASE, SNAKE_CASE)
                .toLowerCase();
    }

    @Transactional
    public void executeTruncate() {
        entityManager.flush();
        entityManager.clear();

        disableIntegrity();
        for (String tableName : tableNames) {
            truncateTable(tableName);
            resetAutoIncrement(tableName);
        }
        enableIntegrity();
    }

    private void disableIntegrity() {
        entityManager.createNativeQuery("SET FOREIGN_KEY_CHECKS = 0")
                .executeUpdate();
    }

    private void truncateTable(final String tableName) {
        entityManager.createNativeQuery(String.format("TRUNCATE TABLE %s", tableName))
                .executeUpdate();
    }

    private void resetAutoIncrement(final String tableName) {
        entityManager.createNativeQuery(String.format("ALTER TABLE %s AUTO_INCREMENT = 1", tableName))
                .executeUpdate();
    }

    private void enableIntegrity() {
        entityManager.createNativeQuery("SET FOREIGN_KEY_CHECKS = 1")
                .executeUpdate();
    }
}
