package com.example.app.ingestion.engine;

import com.example.app.core.HashUtils;
import com.example.app.core.MappingConfig;
import com.example.app.core.MappingRule;
import com.example.app.core.normalizer.NormalizerRegistry;
import com.example.app.ingestion.model.FtmEntity;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Service de mapping dynamique FollowTheMoney (FTM).
 * Transforme un flux JSON de données source en entités FTM à l'aide d'un flux
 * JSON de configuration de mapping.
 */
@Service
public class DynamicFtmMapper {

    private final ObjectMapper objectMapper;
    private final Configuration jsonPathConfig;
    private final NormalizerRegistry normalizerRegistry;

    public DynamicFtmMapper() {
        this(new NormalizerRegistry());
    }

    public DynamicFtmMapper(NormalizerRegistry normalizerRegistry) {
        this.objectMapper = new ObjectMapper();
        this.normalizerRegistry = normalizerRegistry != null ? normalizerRegistry : new NormalizerRegistry();
        this.jsonPathConfig = Configuration.defaultConfiguration()
                .addOptions(Option.DEFAULT_PATH_LEAF_TO_NULL, Option.SUPPRESS_EXCEPTIONS);
    }

    /**
     * Mappe une source de données JSON et un fichier de configuration de mapping
     * (InputStreams)
     * vers une liste d'entités FollowTheMoney (FtmEntity) avec identifiants et
     * relations hachés en SHA-1.
     *
     * @param dataInputStream    flux d'entrée de la source de données JSON
     * @param mappingInputStream flux d'entrée du fichier de mapping JSON
     *                           (MappingConfig)
     * @return la liste des entités FtmEntity générées
     * @throws Exception en cas d'erreur de désérialisation ou de parsing JsonPath
     */
    public List<FtmEntity> map(InputStream dataInputStream, InputStream mappingInputStream) throws Exception {
        List<Map<String, Object>> sourceRecords = objectMapper.readValue(dataInputStream, new TypeReference<>() {
        });
        return map(sourceRecords, mappingInputStream);
    }

    public List<FtmEntity> map(List<Map<String, Object>> sourceRecords, InputStream mappingInputStream)
            throws Exception {
        MappingConfig mappingConfig = objectMapper.readValue(mappingInputStream, MappingConfig.class);
        return map(sourceRecords, mappingConfig);
    }

    public List<FtmEntity> map(List<Map<String, Object>> sourceRecords, MappingConfig mappingConfig) {
        List<FtmEntity> result = new ArrayList<>();
        for (Map<String, Object> sourceRecord : sourceRecords) {
            DocumentContext ctx = JsonPath.using(jsonPathConfig).parse(sourceRecord);
            List<MappingRule> matchedRules = mappingConfig.findMatchingRules(ctx);
            List<FtmEntity> entitiesForRecord = new ArrayList<>();
            for (MappingRule rule : matchedRules) {
                FtmEntity entity = mapEntity(ctx, rule, entitiesForRecord);
                if (entity != null) {
                    entitiesForRecord.add(entity);
                    result.add(entity);
                }
            }
        }
        return result;
    }

    /**
     * Construit une entité FTM à partir d'un enregistrement et d'une règle de
     * mapping,
     * en reliant si besoin les entités déjà créées pour le même enregistrement.
     */
    private FtmEntity mapEntity(DocumentContext ctx, MappingRule rule, List<FtmEntity> siblingEntities) {
        FtmEntity entity = FtmEntity.builder()
                .schema(rule.getFtmSchema())
                .build();

        // 1. Extraction, normalisation sémantique et assignation des propriétés FTM
        for (Map.Entry<String, String> entry : rule.getPropertiesMapping().entrySet()) {
            String ftmProperty = entry.getKey();
            String expr = entry.getValue();

            // Résolution du type sémantique FTM ("date", "country", "currency", "entity",
            // etc.)
            String propertyType = rule.getPropertyType(ftmProperty);

            List<String> extractedValues = extractValues(ctx, expr, siblingEntities, propertyType);
            for (String val : extractedValues) {
                if (val != null && !val.trim().isEmpty()) {
                    String normalizedValue = normalizerRegistry.normalize(propertyType, val);
                    if (normalizedValue != null) {
                        entity.addProperty(ftmProperty, normalizedValue);
                    }
                }
            }
        }

        // 2. Extraction ou calcul automatique de l'identifiant FTM
        List<String> idValues = new ArrayList<>();
        if (rule.getIdJsonPaths() != null && !rule.getIdJsonPaths().isEmpty()) {
            for (String idPath : rule.getIdJsonPaths()) {
                idValues.addAll(extractValues(ctx, idPath, siblingEntities, null));
            }
        }

        String rawId;
        if (!idValues.isEmpty()) {
            rawId = String.join(":", idValues);
        } else {
            // Calcul automatique déterministe de l'identifiant à partir du schéma et des
            // propriétés clés
            rawId = generateAutomaticId(ctx, rule.getFtmSchema(), entity);
        }

        entity.setId(HashUtils.sha1(rawId));
        return entity;
    }

    /**
     * Génère un identifiant déterministe automatique si idJsonPaths n'est pas
     * spécifié.
     */
    private String generateAutomaticId(DocumentContext ctx, String schema, FtmEntity entity) {
        // 1. Essai avec des champs d'identifiant communs dans la source
        for (String idField : List.of("$.logicalId", "$.id", "$.code", "$.identifier", "$.key")) {
            List<String> vals = readAndFlatten(ctx, idField);
            if (!vals.isEmpty() && !vals.get(0).isBlank()) {
                return schema + ":" + vals.get(0);
            }
        }

        // 2. Essai avec les propriétés d'association (ex: Occupancy -> holder + post)
        if (entity.getProperties().containsKey("holder") && entity.getProperties().containsKey("post")) {
            String holder = entity.getProperties().get("holder").get(0);
            String post = entity.getProperties().get("post").get(0);
            String start = entity.getProperties().containsKey("startDate")
                    ? entity.getProperties().get("startDate").get(0)
                    : "";
            return schema + ":" + holder + ":" + post + ":" + start;
        }

        // 3. Essai avec la propriété "name"
        if (entity.getProperties().containsKey("name") && !entity.getProperties().get("name").isEmpty()) {
            return schema + ":" + entity.getProperties().get("name").get(0);
        }

        // 4. Concaténation ordonnée de toutes les propriétés disponibles
        String props = entity.getProperties().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .flatMap(e -> e.getValue().stream().map(v -> e.getKey() + "=" + v))
                .reduce((a, b) -> a + "|" + b)
                .orElse("unknown");

        return schema + ":" + props;
    }

    /**
     * Extraction robuste de valeurs :
     * - Si l'expression contient des accolades {...} : évaluée comme un template de
     * concaténation (ex: "{$.firstName} {$.lastName}").
     * - Si l'expression commence par '$' ou '@' : évaluée comme JsonPath.
     * - Si elle correspond au schéma d'une entité soeur : injecte l'ID de cette
     * entité (relation automatique).
     * - Sinon : traitée comme une valeur constante directe (littéral).
     */
    private List<String> extractValues(DocumentContext ctx, String expr, List<FtmEntity> siblingEntities,
            String propertyType) {
        if (expr == null || expr.isBlank()) {
            return Collections.emptyList();
        }

        // 1. Cas Template de concaténation (ex: "{$.firstName} {$.lastName}" ou
        // "{firstName} {lastName}")
        if (expr.contains("{") && expr.contains("}")) {
            String evaluated = evaluateTemplate(ctx, expr);
            return evaluated.isEmpty() ? Collections.emptyList() : List.of(evaluated);
        }

        // 2. Cas JsonPath
        if (expr.startsWith("$") || expr.startsWith("@")) {
            List<String> values = readAndFlatten(ctx, expr);
            if (values.isEmpty() && expr.contains("[*]")) {
                values = readAndFlatten(ctx, expr.replace("[*]", ""));
            }
            return values;
        }

        // 3. Cas référence à une entité liée (ex: "Person", "Position", "Company")
        if ("entity".equalsIgnoreCase(propertyType) || isEntitySchema(expr)) {
            String targetSchema = expr.startsWith("schema:") ? expr.substring(7) : expr;
            for (FtmEntity sibling : siblingEntities) {
                if (sibling.getSchema().equalsIgnoreCase(targetSchema)) {
                    return List.of(sibling.getId());
                }
            }
        }

        // 4. Cas constante littérale directe (ex: "LU", "current", "role.pep")
        return List.of(expr);
    }

    /**
     * Évalue un template contenant des expressions entre accolades (ex:
     * "{$.firstName} {$.lastName}").
     * Utilise un parcours par index sans aucune expression régulière.
     */
    private String evaluateTemplate(DocumentContext ctx, String template) {
        StringBuilder sb = new StringBuilder();
        int cursor = 0;
        int len = template.length();

        while (cursor < len) {
            int start = template.indexOf('{', cursor);
            if (start == -1) {
                sb.append(template.substring(cursor));
                break;
            }
            sb.append(template, cursor, start);

            int end = template.indexOf('}', start);
            if (end == -1) {
                sb.append(template.substring(start));
                break;
            }

            String path = template.substring(start + 1, end).trim();
            if (!path.startsWith("$") && !path.startsWith("@")) {
                path = "$." + path;
            }

            List<String> vals = readAndFlatten(ctx, path);
            if (!vals.isEmpty() && vals.get(0) != null) {
                sb.append(vals.get(0));
            }

            cursor = end + 1;
        }

        String res = sb.toString().trim();
        while (res.contains("  ")) {
            res = res.replace("  ", " ");
        }
        return res;
    }

    private boolean isEntitySchema(String text) {
        return text != null && (text.equalsIgnoreCase("Person")
                || text.equalsIgnoreCase("Company")
                || text.equalsIgnoreCase("Organization")
                || text.equalsIgnoreCase("Position")
                || text.equalsIgnoreCase("Vessel")
                || text.equalsIgnoreCase("Airplane"));
    }

    private List<String> readAndFlatten(DocumentContext ctx, String path) {
        try {
            return flatten(ctx.read(path));
        } catch (Exception ignored) {
            return Collections.emptyList();
        }
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
            for (Object val : map.values()) {
                results.addAll(flatten(val));
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
