package com.example.app.execution;

import com.example.app.core.DataSourceConfig;
import com.example.app.ingestion.IngestionException;
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
 * Une exécution du pipeline FTM pour une source : quand, avec quel résultat, et pourquoi elle a échoué.
 * La configuration de la source reste dans {@link DataSourceConfig}.
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

    /** Fichiers source lus (plusieurs pages pour certaines sources). */
    @Column(name = "source_files")
    private Integer sourceFiles;

    /** Enregistrements JSON obtenus après conversion. */
    @Column(name = "source_records")
    private Integer sourceRecords;

    /** Enregistrements gardés par les filtres du mapping. */
    @Column(name = "mapped_records")
    private Integer mappedRecords;

    @Column(name = "entity_count")
    private Integer entityCount;

    @Column(name = "person_count")
    private Integer personCount;

    /** Nombre d'entités par schéma, en JSON : {@code {"Person": 60, "Occupancy": 60, "Position": 1}}. */
    @Column(name = "entities_by_schema", length = 1000)
    private String entitiesBySchema;

    /** Fichier NDJSON produit. */
    @Column(name = "output_path", length = 1000)
    private String outputPath;

    /** Étape en échec (téléchargement, conversion, mapping...). */
    @Enumerated(EnumType.STRING)
    @Column(name = "failed_step", length = 20)
    private IngestionException.Step failedStep;

    /** Message d'erreur affiché à l'utilisateur. */
    @Column(name = "error_message", length = 2000)
    private String errorMessage;

    /** Trace technique complète, pour le diagnostic. */
    @Column(name = "error_detail", columnDefinition = "TEXT")
    private String errorDetail;
}
