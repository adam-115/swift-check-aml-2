package com.example.app.execution;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository Spring Data JPA de l'historique des exécutions.
 */
@Repository
public interface SourceExecutionRepository extends JpaRepository<SourceExecution, Long> {

    List<SourceExecution> findAllByOrderByStartedAtDesc(Pageable pageable);

    List<SourceExecution> findByDataSourceConfigIdOrderByStartedAtDesc(Long dataSourceConfigId, Pageable pageable);

    Optional<SourceExecution> findFirstByDataSourceConfigIdOrderByStartedAtDesc(Long dataSourceConfigId);

    Optional<SourceExecution> findFirstByDataSourceConfigIdAndStatusOrderByStartedAtDesc(Long dataSourceConfigId,
                                                                                          ExecutionStatus status);

    boolean existsByDataSourceConfigIdAndStatus(Long dataSourceConfigId, ExecutionStatus status);

    /** Exécutions restées « en cours » après un arrêt de l'application : elles ne se termineront jamais. */
    @Modifying
    @Query("update SourceExecution e set e.status = com.example.app.execution.ExecutionStatus.FAILED, "
            + "e.finishedAt = :now, e.errorMessage = :message "
            + "where e.status = com.example.app.execution.ExecutionStatus.RUNNING")
    int failInterrupted(LocalDateTime now, String message);
}
