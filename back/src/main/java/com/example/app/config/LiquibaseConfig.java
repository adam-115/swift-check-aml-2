package com.example.app.config;

import liquibase.integration.spring.SpringLiquibase;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jpa.autoconfigure.EntityManagerFactoryDependsOnPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * Configuration explicite de Liquibase pour exécuter les migrations au démarrage.
 */
@Configuration
public class LiquibaseConfig {

    /**
     * Les migrations passent avant la validation du schéma par Hibernate (ddl-auto=validate) :
     * sans cela, une nouvelle table est déclarée manquante au premier démarrage.
     */
    @Bean
    public static EntityManagerFactoryDependsOnPostProcessor entityManagerFactoryDependsOnLiquibase() {
        return new EntityManagerFactoryDependsOnPostProcessor("liquibase");
    }

    @Value("${spring.liquibase.change-log:classpath:db/changelog/db.changelog-master.xml}")
    private String changeLog;

    @Value("${spring.liquibase.enabled:true}")
    private boolean enabled;

    @Bean
    public SpringLiquibase liquibase(DataSource dataSource) {
        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(dataSource);
        liquibase.setChangeLog(changeLog);
        liquibase.setShouldRun(enabled);
        return liquibase;
    }
}
