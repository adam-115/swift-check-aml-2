package com.example.app.ingestion.model;

import java.util.List;
import java.util.Map;

/**
 * Résultat d'une génération FTM : chiffres de chaque étape et entités produites.
 *
 * @param sourceFiles     nombre de fichiers source lus
 * @param sourceRecords   enregistrements JSON obtenus après conversion
 * @param mappedRecords   enregistrements gardés par les filtres du mapping
 * @param entities        entités FTM, fusionnées par identifiant
 * @param entitiesBySchema nombre d'entités par schéma FTM
 */
public record GenerationReport(int sourceFiles,
                               int sourceRecords,
                               int mappedRecords,
                               List<FtmEntity> entities,
                               Map<String, Long> entitiesBySchema) {

    public long count(String schema) {
        return entitiesBySchema.getOrDefault(schema, 0L);
    }
}
