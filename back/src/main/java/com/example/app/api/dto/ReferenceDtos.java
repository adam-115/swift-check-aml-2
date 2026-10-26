package com.example.app.api.dto;

import com.example.app.core.entities.PepCategory;
import com.example.app.core.entities.PepFunction;
import com.example.app.core.pep.PepCategoryKind;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Corps de requête et réponses du référentiel PEP (pays, catégories, fonctions).
 */
public final class ReferenceDtos {

    private ReferenceDtos() {
    }

    /** Création ou modification d'un pays. */
    public record CountryRequest(
            @NotBlank(message = "obligatoire") @Pattern(regexp = "^[A-Za-z]{2}$", message = "code ISO à 2 lettres")
            String iso,
            @NotBlank(message = "obligatoire") @Size(max = 100, message = "100 caractères au plus") String name,
            Boolean euMember,
            @Size(max = 500, message = "500 caractères au plus") String legalBasis,
            @NotNull(message = "obligatoire") @Min(value = 0, message = "positif")
            @Max(value = 240, message = "240 mois au plus") Integer retentionMonths,
            @Size(max = 255, message = "255 caractères au plus") String officialListReference,
            LocalDate officialListDate,
            @Size(max = 1024, message = "1024 caractères au plus") String officialListUrl) {
    }

    /** Catégorie PEP, avec le nombre de fonctions qu'elle contient. */
    public record CategoryDto(Long id, String countryIso, String code, String label, PepCategoryKind kind,
                              int riskClass, int sortOrder, String legalReference, long functionCount) {

        public static CategoryDto of(PepCategory c, long functionCount) {
            return new CategoryDto(c.getId(), c.getCountry().getIso(), c.getCode(), c.getLabel(), c.getKind(),
                    c.getRiskClass(), c.getSortOrder(), c.getLegalReference(), functionCount);
        }
    }

    /** Création ou modification d'une catégorie PEP. */
    public record CategoryRequest(
            @NotBlank(message = "obligatoire") @Pattern(regexp = "^[A-Za-z]{2}$", message = "code ISO à 2 lettres")
            String countryIso,
            @NotBlank(message = "obligatoire") @Size(max = 20, message = "20 caractères au plus") String code,
            @NotBlank(message = "obligatoire") @Size(max = 500, message = "500 caractères au plus") String label,
            @NotNull(message = "obligatoire") PepCategoryKind kind,
            @NotNull(message = "obligatoire") @Min(value = 1, message = "entre 1 et 4")
            @Max(value = 4, message = "entre 1 et 4") Integer riskClass,
            @Min(value = 0, message = "positif") Integer sortOrder,
            @Size(max = 500, message = "500 caractères au plus") String legalReference) {
    }

    /** Fonction PEP, avec sa catégorie et le nombre de sources qui la couvrent. */
    public record FunctionDto(Long id, Long categoryId, String categoryCode, String categoryLabel,
                              PepCategoryKind categoryKind, int categoryOrder, String countryIso, String label,
                              String institution, Integer expectedHolders, long sourceCount) {

        public static FunctionDto of(PepFunction f, long sourceCount) {
            PepCategory c = f.getCategory();
            return new FunctionDto(f.getId(), c.getId(), c.getCode(), c.getLabel(), c.getKind(), c.getSortOrder(),
                    c.getCountry().getIso(), f.getLabel(), f.getInstitution(), f.getExpectedHolders(), sourceCount);
        }
    }

    /** Création ou modification d'une fonction PEP. */
    public record FunctionRequest(
            @NotNull(message = "obligatoire") Long categoryId,
            @NotBlank(message = "obligatoire") @Size(max = 500, message = "500 caractères au plus") String label,
            @Size(max = 255, message = "255 caractères au plus") String institution,
            @Min(value = 0, message = "positif") Integer expectedHolders) {
    }
}
