package com.example.app.core;

import com.example.app.ingestion.model.SourceFormat;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Entité JPA et modèle de configuration pour une source de données.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "data_source_config")
public class DataSourceConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_name")
    private String sourceName;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_format", nullable = false)
    private SourceFormat sourceFormat;

    @Column(name = "external_source_path", length = 1000)
    private String externalSourcePath;

    @Column(name = "local_source_path", length = 1000)
    private String localSourcePath;

    @Column(name = "initial_json_source_path", length = 1000)
    private String initialJsonSourcePath;

    @Column(name = "mapping_file_path", length = 1000)
    private String mappingFilePath;

    @Column(name = "ftm_output_path", length = 1000)
    private String ftmOutputPath;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
