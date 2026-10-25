package com.example.app.api.dto;

import jakarta.validation.constraints.Size;

/**
 * Modification d'une source depuis l'interface. Un champ absent ({@code null}) n'est pas modifié.
 *
 * @param enabled            active ou désactive la source
 * @param externalSourcePath nouvelle adresse (plusieurs adresses : une par ligne)
 */
public record SourceUpdateRequest(Boolean enabled,
                                  @Size(max = 4000, message = "4000 caractères au plus") String externalSourcePath) {
}
