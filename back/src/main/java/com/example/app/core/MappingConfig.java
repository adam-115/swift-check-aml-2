package com.example.app.core;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Modèle universel représentant n'importe quel fichier de configuration de mapping (Zero-DTO).
 * Ce modèle unique est capable de représenter les règles pour l'UE, l'OFAC, l'ONU, ou toute autre source.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MappingConfig {

    /**
     * Nom du jeu de règles (ex: "EU Financial Sanctions Mapping Configuration").
     */
    private String name;

    /**
     * Description optionnelle de la source ou de la version.
     */
    private String description;

    /**
     * Liste des règles de mapping pour chaque entité cible.
     */
    @Builder.Default
    private List<MappingRule> rules = new ArrayList<>();

    /**
     * Recherche la règle appropriée correspondant à un fragment JSON donné (via targetNodeName et discriminateur conditionnel).
     */
    @JsonIgnore
    public Optional<MappingRule> findMatchingRule(DocumentContext documentContext) {
        if (rules == null || rules.isEmpty() || documentContext == null) {
            return Optional.empty();
        }
        return rules.stream()
                .filter(rule -> rule.matches(documentContext))
                .findFirst();
    }

    /**
     * Recherche la règle appropriée pour un objet/Map JSON.
     */
    @JsonIgnore
    public Optional<MappingRule> findMatchingRule(Object jsonSource) {
        if (jsonSource == null) {
            return Optional.empty();
        }
        return findMatchingRule(JsonPath.parse(jsonSource));
    }
}
