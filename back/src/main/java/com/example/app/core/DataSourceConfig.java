package com.example.app.core;

import com.example.app.core.pep.Country;
import com.example.app.ingestion.model.SourceFormat;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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

    /**
     * Nom technique unique de la source, en minuscules avec des tirets bas (ex. {@code lu_pep_chamber}).
     * Ajouté à chaque enregistrement ({@code _dataset}) pour préfixer les identifiants FTM.
     */
    @Column(name = "source_name")
    private String sourceName;

    /** Nom lisible, ex. « Députés en fonction (Luxembourg) ». */
    @Column(name = "label")
    private String label;

    @Column(name = "description", length = 1000)
    private String description;

    /** Mention à citer quand les données sont réutilisées, ex. « Chambre des Députés, data.public.lu ». */
    @Column(name = "attribution", length = 1000)
    private String attribution;

    /** Options de conversion en JSON (encodage, sélecteurs HTML), voir {@code ConverterOptions}. */
    @Column(name = "converter_options", columnDefinition = "TEXT")
    private String converterOptions;

    /** Une source désactivée n'est plus exécutée. */
    @Builder.Default
    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_format", nullable = false)
    private SourceFormat sourceFormat;

    /** Adresse(s) du fichier source : une URL ou un chemin par ligne. */
    @Column(name = "external_source_path", columnDefinition = "TEXT")
    private String externalSourcePath;

    /**
     * Vrai quand la source a été modifiée depuis l'interface : le catalogue des sources ne l'écrase plus
     * au démarrage. Faux tant qu'elle est celle du catalogue.
     */
    @Builder.Default
    @Column(name = "customized", nullable = false)
    private boolean customized = false;

    /** Date de suppression depuis l'interface ; la source n'est plus listée ni exécutée. */
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "local_source_path", length = 1000)
    private String localSourcePath;

    @Column(name = "initial_json_source_path", length = 1000)
    private String initialJsonSourcePath;

    @Column(name = "mapping_file_path", length = 1000)
    private String mappingFilePath;

    @Column(name = "ftm_output_path", length = 1000)
    private String ftmOutputPath;

    /** Sanctions ou PEP. */
    @Enumerated(EnumType.STRING)
    @Column(name = "list_type", length = 20)
    private ListType listType;

    /** Type de source T1 à T7 (open data, API, page web, PDF...). */
    @Enumerated(EnumType.STRING)
    @Column(name = "parser_type", length = 30)
    private ParserType parserType;

    /** Pays de la source ; vide pour une organisation internationale (UE, ONU). */
    @ManyToOne
    @JoinColumn(name = "country_iso")
    private Country country;

    /**
     * Mois pendant lesquels une entité disparue de la source reste publiée.
     * 0 pour les sanctions ; au moins {@link Country#getRetentionMonths()} pour les PEP.
     */
    @Column(name = "retention_months")
    private Integer retentionMonths;

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
