package com.example.app.api.dto;

import com.example.app.core.Completeness;
import com.example.app.core.pep.Country;
import com.example.app.core.pep.PepCategoryKind;
import com.example.app.execution.ExecutionStatus;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Couverture PEP d'un pays : pour chaque catégorie et fonction, les sources qui la couvrent et l'état de
 * leur dernière exécution.
 *
 * @param personCount personnes publiées par la dernière exécution réussie de chaque source PEP du pays
 */
public record CoverageDto(Country country,
                          int officialCategories,
                          int coveredOfficialCategories,
                          int personCount,
                          List<Category> categories) {

    /** COVERED : une source complète a généré ; PARTIAL : seulement une partie ; NONE : rien de généré. */
    public enum Status { COVERED, PARTIAL, NONE }

    public record Category(Long id, String code, String label, PepCategoryKind kind, int riskClass,
                           String legalReference, Status status, List<Function> functions) {
    }

    public record Function(Long id, String label, String institution, Integer expectedHolders, Status status,
                           List<Source> sources) {
    }

    /**
     * Source qui couvre une fonction.
     *
     * @param implemented vrai si la source a un fichier de mapping
     */
    public record Source(Long id, String sourceName, String label, String url, Completeness completeness,
                         boolean includesFormer, boolean enabled, boolean implemented, ExecutionStatus lastStatus,
                         LocalDateTime lastSuccessAt, Integer personCount) {
    }
}
