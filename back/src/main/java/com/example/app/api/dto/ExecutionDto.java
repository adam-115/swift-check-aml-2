package com.example.app.api.dto;

import com.example.app.api.ExecutionService.FileKind;
import com.example.app.core.ExecutionStatus;
import com.example.app.core.entities.SourceExecution;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Une exécution d'une source et ses fichiers téléchargeables.
 *
 * @param failedStep étape en échec : DOWNLOAD, CONVERSION, MAPPING ou EXPORT
 * @param errorCause cause première de l'échec
 * @param errorStack pile d'appels, seulement dans le détail d'une exécution
 */
public record ExecutionDto(Long id,
                           Long sourceId,
                           String sourceName,
                           String sourceLabel,
                           ExecutionStatus status,
                           LocalDateTime startedAt,
                           LocalDateTime finishedAt,
                           Long durationMs,
                           Integer entityCount,
                           String schemaCounts,
                           String failedStep,
                           String errorMessage,
                           String errorCause,
                           String errorStack,
                           List<ExecutionFile> files) {

    /** Fichier archivé : {@code size} vaut null s'il n'a pas été produit. */
    public record ExecutionFile(String kind, String label, Long size, String url) {
    }

    /** Résumé, sans la pile d'appels. */
    public static ExecutionDto of(SourceExecution e) {
        return of(e, false);
    }

    /** Détail, avec la pile d'appels. */
    public static ExecutionDto detail(SourceExecution e) {
        return of(e, true);
    }

    private static ExecutionDto of(SourceExecution e, boolean withStack) {
        String base = "/api/executions/" + e.getId() + "/files/";
        List<ExecutionFile> files = List.of(
                new ExecutionFile("source", FileKind.SOURCE.label, e.getSourceSize(), base + "source"),
                new ExecutionFile("json", FileKind.JSON.label, e.getJsonSize(), base + "json"),
                new ExecutionFile("mapping", FileKind.MAPPING.label, e.getMappingSize(), base + "mapping"),
                new ExecutionFile("ftm", FileKind.FTM.label, e.getFtmSize(), base + "ftm"));
        var source = e.getDataSourceConfig();
        return new ExecutionDto(e.getId(), source.getId(), source.getSourceName(), source.getLabel(), e.getStatus(),
                e.getStartedAt(), e.getFinishedAt(), e.getDurationMs(), e.getEntityCount(), e.getSchemaCounts(),
                e.getFailedStep(), e.getErrorMessage(), e.getErrorCause(), withStack ? e.getErrorStack() : null, files);
    }
}
