package com.example.app.catalog;

import com.example.app.core.Completeness;
import com.example.app.core.ListType;
import com.example.app.core.ParserType;
import com.example.app.ingestion.model.SourceFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/**
 * Définition d'une source dans le catalogue ({@code classpath:catalog/**}/*.source.json) : tout ce qu'il faut
 * pour créer sa {@code DataSourceConfig} et sa couverture PEP, plus les fichiers de test hors ligne.
 *
 * @param urls             adresses à télécharger (plusieurs pages possibles)
 * @param converterOptions options de conversion (encodage, sélecteurs HTML), voir {@code ConverterOptions}
 * @param mapping          fichier de mapping ; absent si la source n'est pas encore implémentée
 * @param test             fichiers de test (sous {@code fixtures/<pays>/<sourceName>/}) et résultat attendu
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SourceDefinition(String sourceName,
                               String label,
                               String description,
                               String attribution,
                               ListType listType,
                               ParserType parserType,
                               SourceFormat sourceFormat,
                               String country,
                               Integer retentionMonths,
                               List<String> urls,
                               JsonNode converterOptions,
                               String mapping,
                               List<Coverage> coverage,
                               Test test) {

    /** Fonction PEP couverte, désignée par le code de sa catégorie et son libellé. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Coverage(String category, String function, Completeness completeness, boolean includesFormer) {
    }

    /**
     * Test hors ligne.
     *
     * @param fixtures   fichiers lus à la place des adresses, dans le même ordre
     * @param minPersons nombre minimal de personnes attendu
     * @param samples    noms (schéma Person) qui doivent apparaître
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Test(List<String> fixtures, Integer minPersons, List<String> samples) {
    }
}
