package com.example.app.api.dto;

import com.example.app.core.pep.PepCategoryKind;
import com.example.app.core.pep.PepFunction;

/**
 * Fonction PEP proposée dans le formulaire d'une source (couverture).
 */
public record PepFunctionDto(Long id,
                             String categoryCode,
                             String categoryLabel,
                             PepCategoryKind categoryKind,
                             int categoryOrder,
                             String label,
                             String institution) {

    public static PepFunctionDto of(PepFunction f) {
        return new PepFunctionDto(f.getId(), f.getCategory().getCode(), f.getCategory().getLabel(),
                f.getCategory().getKind(), f.getCategory().getSortOrder(), f.getLabel(), f.getInstitution());
    }
}
