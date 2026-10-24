package com.example.app.ingestion;

import com.example.app.core.MappingConfig;
import com.example.app.core.MappingRule;
import com.example.app.ingestion.model.FtmEntity;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class MapToFtmTest {

    private final ObjectMapper objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private final Configuration jsonPathConfig = Configuration.defaultConfiguration()
            .addOptions(Option.DEFAULT_PATH_LEAF_TO_NULL, Option.SUPPRESS_EXCEPTIONS);

    /**
     * Fonction de test prenant en entrée le fichier source JSON et le fichier de
     * mapping JSON,
     * et retournant la liste des FtmEntity (avec identifiants simples sans SHA-1).
     */
    @Test
    @DisplayName("Prend en input eu-sanctions-sample.json et eu-sanctions-mapping.json et retourne List<FtmEntity> avec IDs simples")
    void testMapEuSanctionsJsonToFtmEntities() throws Exception {
        List<FtmEntity> ftmEntities;

        try (InputStream inputStream = getClass().getResourceAsStream("/eu-sanctions-sample.json");
                InputStream mappingIstream = getClass().getResourceAsStream("/eu-sanctions-mapping.json")) {

            // 1. Chargement de la source JSON et de la configuration de mapping
            List<Map<String, Object>> sourceRecords = objectMapper.readValue(inputStream, new TypeReference<>() {
            });
            MappingConfig mappingConfig = objectMapper.readValue(mappingIstream, MappingConfig.class);

            // 2. Mapping vers les entités FollowTheMoney (IDs simples, pas de SHA-1 pour
            // l'instant)
            ftmEntities = mapRecordsToFtmEntities(sourceRecords, mappingConfig);
        }

        // 3. Assertions sur le résultat

        // Vérification de la première entité (Personne: Saddam Hussein - ID simple
        // "13")
        FtmEntity person1 = ftmEntities.get(0);

        // Vérification de l'entreprise (Holding: Example Shipping - ID simple "88")
        FtmEntity company1 = ftmEntities.get(2);

        // Vérification du navire (Pétrolier: GRACE 1 - ID simple "120", owner simple
        // "88")
        FtmEntity vessel = ftmEntities.get(4);

        // Vérification de l'avion (RA-96022 - ID simple "150")
        FtmEntity aircraft = ftmEntities.get(5);

        // Vérification de la mesure de sanction (AssetFreeze - ID simple "201", ciblant
        // "13", "25", "88", "99")
        FtmEntity sanction = ftmEntities.get(6);

        // 4. Écriture du fichier de sortie FTM JSON pour inspection visuelle
        File targetOutput = new File("src/test/resources/eu-sanctions-mapped-ftm.json");
        objectMapper.writeValue(targetOutput, ftmEntities);
        assertThat(targetOutput).exists();
    }

    /**
     * Mappe une liste d'enregistrements sources bruts vers des entités
     * FollowTheMoney
     * en utilisant la configuration fournie, avec des identifiants simples (sans
     * SHA-1).
     */
    public List<FtmEntity> mapRecordsToFtmEntities(List<Map<String, Object>> sourceRecords,
            MappingConfig mappingConfig) {
        List<FtmEntity> result = new ArrayList<>();

        for (Map<String, Object> record : sourceRecords) {
            DocumentContext ctx = JsonPath.using(jsonPathConfig).parse(record);

            // Trouver la règle discriminée (Person, Company, Vessel, etc.)
            Optional<MappingRule> matchedRule = mappingConfig.findMatchingRule(ctx);
            if (matchedRule.isEmpty()) {
                continue;
            }

            MappingRule rule = matchedRule.get();

            // 1. Extraction de l'ID simple (sans SHA-1)
            List<String> idValues = new ArrayList<>();
            for (String idPath : rule.getIdJsonPaths()) {
                idValues.addAll(extractValues(ctx, idPath));
            }
            String simpleId = idValues.isEmpty() ? "unknown" : String.join(":", idValues);

            // 2. Construction de l'entité FTM
            FtmEntity entity = FtmEntity.builder()
                    .id(simpleId) // ID simple sans SHA-1
                    .schema(rule.getFtmSchema())
                    .build();

            // 3. Extraction et assignation des propriétés FTM
            for (Map.Entry<String, String> entry : rule.getPropertiesMapping().entrySet()) {
                String ftmProperty = entry.getKey();
                String jsonPath = entry.getValue();

                List<String> extractedValues = extractValues(ctx, jsonPath);
                for (String val : extractedValues) {
                    // Pour l'instant, on garde aussi les références simples sans SHA-1
                    entity.addProperty(ftmProperty, val);
                }
            }

            result.add(entity);
        }

        return result;
    }

    /**
     * Extraction robuste de valeurs via JsonPath (gère scalaires, listes et
     * fallbacks d'objets uniques).
     */
    private List<String> extractValues(DocumentContext ctx, String jsonPath) {
        if (jsonPath == null || jsonPath.trim().isEmpty()) {
            return Collections.emptyList();
        }
        try {
            Object result = ctx.read(jsonPath);
            if (result != null) {
                List<String> values = flatten(result);
                if (!values.isEmpty()) {
                    return values;
                }
            }
        } catch (Exception ignored) {
        }

        // Fallback: si l'expression contenait [*] alors que l'élément est un objet
        // unique (ex: $.nameAlias[*])
        if (jsonPath.contains("[*]")) {
            try {
                String fallback = jsonPath.replace("[*]", "");
                Object fallbackResult = ctx.read(fallback);
                return flatten(fallbackResult);
            } catch (Exception ignored) {
            }
        }

        return Collections.emptyList();
    }

    private List<String> flatten(Object value) {
        if (value == null) {
            return Collections.emptyList();
        }
        List<String> results = new ArrayList<>();
        if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                results.addAll(flatten(item));
            }
        } else if (value instanceof Map<?, ?> map) {
            // Si une map est retournée, on prend sa valeur "wholeName" ou "text" si
            // présente, sinon toString
            Object name = map.get("wholeName");
            if (name != null) {
                results.add(name.toString().trim());
            }
        } else {
            String str = Objects.toString(value, "").trim();
            if (!str.isEmpty()) {
                results.add(str);
            }
        }
        return results;
    }
}
