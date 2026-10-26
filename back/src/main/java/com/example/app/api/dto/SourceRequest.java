package com.example.app.api.dto;

import com.example.app.core.Completeness;
import com.example.app.core.ListType;
import com.example.app.core.ParserType;
import com.example.app.ingestion.model.SourceFormat;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Création ou modification d'une source (POST et PUT /api/sources).
 *
 * @param pepFunctionId      fonction PEP principale ; toujours ajoutée à la couverture
 * @param externalSourcePath adresse(s) du fichier source, une par ligne
 * @param coverage           fonctions PEP couvertes
 */
public record SourceRequest(
        @NotBlank(message = "obligatoire")
        @Pattern(regexp = "^[a-z][a-z0-9_-]{2,99}$",
                message = "minuscules, chiffres, - et _, 3 à 100 caractères, en commençant par une lettre")
        String sourceName,

        @NotBlank(message = "obligatoire") @Size(max = 255, message = "255 caractères au plus") String label,

        @Size(max = 1000, message = "1000 caractères au plus") String description,

        @Size(max = 1000, message = "1000 caractères au plus") String attribution,

        @NotNull(message = "obligatoire") SourceFormat sourceFormat,

        ListType listType,

        ParserType parserType,

        @Pattern(regexp = "^[A-Za-z]{2}$", message = "code pays ISO à 2 lettres") String countryIso,

        Long pepFunctionId,

        @Min(value = 0, message = "positif") @Max(value = 240, message = "240 mois au plus") Integer retentionMonths,

        Boolean enabled,

        @NotBlank(message = "obligatoire") @Size(max = 4000, message = "4000 caractères au plus")
        String externalSourcePath,

        @Size(max = 1000, message = "1000 caractères au plus") String localSourcePath,

        @Size(max = 1000, message = "1000 caractères au plus") String initialJsonSourcePath,

        @Size(max = 1000, message = "1000 caractères au plus") String mappingFilePath,

        @Size(max = 1000, message = "1000 caractères au plus") String ftmOutputPath,

        @Valid List<CoverageItem> coverage) {

    /** Fonction PEP couverte. */
    public record CoverageItem(@NotNull(message = "obligatoire") Long functionId,
                               @NotNull(message = "obligatoire") Completeness completeness,
                               Boolean includesFormer) {
    }
}
