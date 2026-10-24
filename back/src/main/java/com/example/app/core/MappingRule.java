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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Définition d'une règle de mapping unitaire (Zero-DTO).
 * Applicable à n'importe quel format source et schéma cible (Person, Company, Vessel, Airplane, Sanction, etc.).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MappingRule {

    /**
     * Nom du nœud ou de la balise source ciblée (ex: "sanctionEntity", "sdnEntry", "row").
     */
    private String targetNodeName;

    /**
     * Expression JsonPath discriminante optionnelle (ex: "$.subjectType.code", "$.sdnType", "$.GroupType").
     * Si null ou vide, la règle s'applique par défaut sans condition.
     */
    private String conditionJsonPath;

    /**
     * Valeur attendue de la condition discriminante (ex: "person", "enterprise", "vessel", "Individual").
     */
    private String conditionValue;

    /**
     * Schéma cible de l'ontologie FollowTheMoney (ex: "Person", "Company", "Vessel", "Airplane", "Sanction").
     */
    private String ftmSchema;

    /**
     * Chemins JsonPath utilisés pour extraire les identifiants sources.
     * Leurs valeurs sont concaténées puis hachées en SHA-1 pour obtenir un ID FTM déterministe.
     */
    @Builder.Default
    private List<String> idJsonPaths = new ArrayList<>();

    /**
     * Table de correspondance : clé propriété FTM -> expression JsonPath dans le fragment source.
     * Exemple : "name" -> "$.nameAlias[0].wholeName", "birthDate" -> "$.birthdate.birthdate"
     */
    @Builder.Default
    private Map<String, String> propertiesMapping = new LinkedHashMap<>();

    /**
     * Liste des propriétés dont les valeurs sont des références (clés étrangères) vers d'autres entités.
     * Le moteur hache automatiquement ces valeurs en SHA-1 pour maintenir l'intégrité relationnelle.
     */
    @Builder.Default
    private List<String> foreignKeyProperties = new ArrayList<>();

    /**
     * Vérifie si un fragment de données JSON correspond aux conditions discriminantes de cette règle.
     */
    @JsonIgnore
    public boolean matches(DocumentContext documentContext) {
        if (conditionJsonPath == null || conditionJsonPath.trim().isEmpty()) {
            return true;
        }
        if (documentContext == null) {
            return false;
        }
        try {
            Object val = documentContext.read(conditionJsonPath);
            if (val == null) {
                return false;
            }
            String actualValue = String.valueOf(val).trim();
            return conditionValue != null && conditionValue.equalsIgnoreCase(actualValue);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Variante pour évaluer directement un Map ou objet JSON brut.
     */
    @JsonIgnore
    public boolean matches(Object jsonSource) {
        if (conditionJsonPath == null || conditionJsonPath.trim().isEmpty()) {
            return true;
        }
        if (jsonSource == null) {
            return false;
        }
        return matches(JsonPath.parse(jsonSource));
    }
}
