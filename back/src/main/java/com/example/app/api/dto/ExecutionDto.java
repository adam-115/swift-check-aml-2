package com.example.app.api.dto;

import com.example.app.execution.ExecutionStatus;
import com.example.app.execution.SourceExecution;
import com.example.app.ingestion.IngestionException;

import java.time.LocalDateTime;

/**
 * Une exécution, telle qu'affichée dans les listes et le détail. {@link #errorDetail} n'est rempli que
 * dans le détail.
 */
public record ExecutionDto(Long id,
                           Long sourceId,
                           String sourceName,
                           String sourceLabel,
                           ExecutionStatus status,
                           LocalDateTime startedAt,
                           LocalDateTime finishedAt,
                           Integer sourceFiles,
                           Integer sourceRecords,
                           Integer mappedRecords,
                           Integer entityCount,
                           Integer personCount,
                           String entitiesBySchema,
                           String outputPath,
                           IngestionException.Step failedStep,
                           String errorMessage,
                           String errorDetail) {

    public static ExecutionDto summary(SourceExecution e) {
        return of(e, false);
    }

    public static ExecutionDto detail(SourceExecution e) {
        return of(e, true);
    }

    private static ExecutionDto of(SourceExecution e, boolean withDetail) {
        var source = e.getDataSourceConfig();
        return new ExecutionDto(e.getId(), source.getId(), source.getSourceName(), source.getLabel(), e.getStatus(),
                e.getStartedAt(), e.getFinishedAt(), e.getSourceFiles(), e.getSourceRecords(), e.getMappedRecords(),
                e.getEntityCount(), e.getPersonCount(), e.getEntitiesBySchema(), e.getOutputPath(),
                e.getFailedStep(), e.getErrorMessage(), withDetail ? e.getErrorDetail() : null);
    }
}
