package com.example.app.core.entities;

import com.example.app.core.ExecutionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Une exécution de {@code FtmGeneratorService} pour une source. Les quatre fichiers produits ou utilisés (source,
 * JSON intermédiaire, mapping, FTM) sont archivés dans {@link #archiveDir} ; leur taille est nulle s'ils manquent.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "source_execution")
public class SourceExecution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "data_source_config_id", nullable = false)
    private DataSourceConfig dataSourceConfig;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ExecutionStatus status;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Column(name = "duration_ms")
    private Long durationMs;

    /** Nombre total d'entités FTM générées. */
    @Column(name = "entity_count")
    private Integer entityCount;

    /** Entités par schéma, ex. « Occupancy: 328, Person: 328, Position: 328 ». */
    @Column(name = "schema_counts", length = 1000)
    private String schemaCounts;

    /** Chaîne des erreurs, de l'exception levée à sa cause première. */
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    /** Étape en échec : DOWNLOAD, CONVERSION, MAPPING ou EXPORT. */
    @Column(name = "failed_step", length = 20)
    private String failedStep;

    /** Cause première de l'échec, ex. « FileNotFoundException : Impossible de trouver le fichier : … ». */
    @Column(name = "error_cause", columnDefinition = "TEXT")
    private String errorCause;

    /** Pile d'appels complète de l'exception. */
    @Column(name = "error_stack", columnDefinition = "MEDIUMTEXT")
    private String errorStack;

    /** Dossier d'archive des fichiers de l'exécution, ex. {@code data/executions/42}. */
    @Column(name = "archive_dir", length = 1000)
    private String archiveDir;

    @Column(name = "source_size")
    private Long sourceSize;

    @Column(name = "json_size")
    private Long jsonSize;

    @Column(name = "mapping_size")
    private Long mappingSize;

    @Column(name = "ftm_size")
    private Long ftmSize;
}
