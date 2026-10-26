package com.example.app.api.dto;

import java.util.List;
import java.util.Map;

/**
 * Une page des entités FTM d'une exécution.
 *
 * @param total      nombre d'entités distinctes du fichier
 * @param matching   nombre d'entités qui répondent aux filtres
 * @param schemas    nombre d'entités distinctes par schéma (sans filtre)
 * @param references libellés des entités citées par la page, par identifiant
 */
public record EntityPage(long total,
                         long matching,
                         int page,
                         int size,
                         Map<String, Long> schemas,
                         List<EntityItem> items,
                         Map<String, EntityRef> references) {

    public record EntityItem(String id, String schema, String caption, Map<String, List<String>> properties) {
    }

    public record EntityRef(String schema, String caption) {
    }
}
