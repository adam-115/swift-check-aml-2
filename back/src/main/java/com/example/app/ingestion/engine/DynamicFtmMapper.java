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

    public List<FtmEntity> map(List<Map<String, Object>> sourceRecords, InputStream mappingInputStream) throws Exception {
        MappingConfig mappingConfig = objectMapper.readValue(mappingInputStream, MappingConfig.class);
        return map(sourceRecords, mappingConfig);
    }

    public List<FtmEntity> map(List<Map<String, Object>> sourceRecords, MappingConfig mappingConfig) {
        List<FtmEntity> result = new ArrayList<>();
        for (Map<String, Object> record : sourceRecords) {
            DocumentContext ctx = JsonPath.using(jsonPathConfig).parse(record);
            mappingConfig.findMatchingRule(ctx)
                    .map(rule -> mapEntity(ctx, rule))
                    .ifPresent(result::add);
        }
        return result;
    }

    /**
     * Construit une entité FTM à partir d'un enregistrement et d'une règle de
     * mapping.
     */
    private FtmEntity mapEntity(DocumentContext ctx, MappingRule rule) {
        // 1. Extraction et hachage SHA-1 de l'ID primaire de l'entité
        List<String> idValues = new ArrayList<>();
        for (String idPath : rule.getIdJsonPaths()) {
            idValues.addAll(extractValues(ctx, idPath));
        }
        String rawId = idValues.isEmpty() ? "unknown" : String.join(":", idValues);
        String hashedId = HashUtils.sha1(rawId);

        FtmEntity entity = FtmEntity.builder()
                .id(hashedId)
                .schema(rule.getFtmSchema())
                .build();

        // 2. Extraction, normalisation sémantique et assignation des propriétés FTM
        for (Map.Entry<String, String> entry : rule.getPropertiesMapping().entrySet()) {
            String ftmProperty = entry.getKey();
            String jsonPath = entry.getValue();

            // Résolution du type sémantique FTM ("date", "country", "currency", "entity", etc.)
            String propertyType = rule.getPropertyType(ftmProperty);

            List<String> extractedValues = extractValues(ctx, jsonPath);
            for (String val : extractedValues) {
                if (val != null && !val.trim().isEmpty()) {
                    String normalizedValue = normalizerRegistry.normalize(propertyType, val);
                    entity.addProperty(ftmProperty, normalizedValue);
                }
            }
        }

        return entity;
    }

    /**
     * Extraction robuste de valeurs via JsonPath (nominal puis fallback sans [*]).
     */
    private List<String> extractValues(DocumentContext ctx, String jsonPath) {
        if (jsonPath == null || jsonPath.isBlank()) {
            return Collections.emptyList();
        }

        // 1. Essai avec le chemin d'origine
        List<String> values = readAndFlatten(ctx, jsonPath);

        // 2. Si vide et que le chemin contient [*], essai de secours sans [*] (cas de
        // l'objet unique)
        if (values.isEmpty() && jsonPath.contains("[*]")) {
            values = readAndFlatten(ctx, jsonPath.replace("[*]", ""));
        }

        return values;
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
