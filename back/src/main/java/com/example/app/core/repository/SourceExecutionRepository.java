package com.example.app.core.repository;

import com.example.app.core.ExecutionStatus;
import com.example.app.core.entities.SourceExecution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Historique des exécutions de sources.
 */
@Repository
public interface SourceExecutionRepository extends JpaRepository<SourceExecution, Long> {

    List<SourceExecution> findByDataSourceConfigIdOrderByIdDesc(Long dataSourceConfigId);

    Optional<SourceExecution> findFirstByDataSourceConfigIdOrderByIdDesc(Long dataSourceConfigId);

    boolean existsByDataSourceConfigIdAndStatus(Long dataSourceConfigId, ExecutionStatus status);

    List<SourceExecution> findByStatus(ExecutionStatus status);

    /** Les 500 dernières exécutions, toutes sources confondues. */
    List<SourceExecution> findTop500ByOrderByIdDesc();
}
