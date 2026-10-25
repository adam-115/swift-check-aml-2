package com.example.app.core.repository;

import com.example.app.core.DataSourceConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository Spring Data JPA pour la persistance et la gestion des configurations de sources de données.
 */
@Repository
public interface DataSourceConfigRepository extends JpaRepository<DataSourceConfig, Long> {

    Optional<DataSourceConfig> findBySourceName(String sourceName);
}
