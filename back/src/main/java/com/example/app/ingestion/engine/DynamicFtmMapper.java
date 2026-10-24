package com.example.app.ingestion.engine;

import com.example.app.ingestion.model.FragmentMappingRule;
import com.example.app.ingestion.model.FtmEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.PathNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Dynamic Zero-DTO mapper producing FollowTheMoney (FTM) ontology entities
 * from arbitrary JSON fragments based on configuration rules.
 *
 * Guarantees:
 * - Zero DTOs: operates on dynamic JSON structures via JsonPath.
 * - Deterministic IDs: SHA-1 hashed from source identifiers (no random UUIDs).
 * - All FTM properties are formatted as lists of strings.
 * - Silent handling of JsonPath PathNotFoundException for optional fields.
 * - Automatic SHA-1 hashing for foreign key reference properties.
 */
@Slf4j
@Component
public class DynamicFtmMapper {

    private final Configuration jsonPathConfig;
    private final ObjectMapper objectMapper;

    public DynamicFtmMapper() {
        this.objectMapper = new ObjectMapper();
        this.jsonPathConfig = Configuration.defaultConfiguration()
                .addOptions(Option.DEFAULT_PATH_LEAF_TO_NULL, Option.SUPPRESS_EXCEPTIONS);
    }

    /**
     * Maps a dynamic JSON source (String, Map, or Object) to an FtmEntity using the given rule.
     */
    public FtmEntity map(Object jsonSource, FragmentMappingRule rule) {
        if (jsonSource == null || rule == null) {
            return null;
        }

        DocumentContext documentContext = parseToDocumentContext(jsonSource);
        return map(documentContext, rule);
    }

    /**
     * Maps a pre-parsed JsonPath DocumentContext to an FtmEntity using the given rule.
     */
    public FtmEntity map(DocumentContext documentContext, FragmentMappingRule rule) {
        if (documentContext == null || rule == null) {
            return null;
        }

        // 1. Generate deterministic FTM ID (SHA-1, no random UUID)
        String entityId = generateDeterministicId(documentContext, rule);

        FtmEntity entity = FtmEntity.builder()
                .id(entityId)
                .schema(rule.getFtmSchema())
                .build();

        Set<String> foreignKeys = rule.getForeignKeyProperties() != null
                ? new LinkedHashSet<>(rule.getForeignKeyProperties())
                : Collections.emptySet();

        // 2. Map properties dynamically
        if (rule.getPropertiesMapping() != null) {
            for (Map.Entry<String, String> entry : rule.getPropertiesMapping().entrySet()) {
                String ftmProperty = entry.getKey();
                String jsonPathExpression = entry.getValue();

                if (ftmProperty == null || jsonPathExpression == null) {
                    continue;
                }

                List<String> extractedValues = extractValuesSilently(documentContext, jsonPathExpression);
                if (extractedValues.isEmpty()) {
                    continue;
                }

                boolean isForeignKey = foreignKeys.contains(ftmProperty);

                for (String rawVal : extractedValues) {
                    if (rawVal == null || rawVal.trim().isEmpty()) {
                        continue;
                    }
                    String finalVal = isForeignKey ? computeSha1(rawVal.trim()) : rawVal.trim();
                    entity.addProperty(ftmProperty, finalVal);
                }
            }
        }

        return entity;
    }

    /**
     * Maps a collection of dynamic JSON sources using the specified rule.
     */
    public List<FtmEntity> mapList(Collection<?> jsonSources, FragmentMappingRule rule) {
        if (jsonSources == null || rule == null) {
            return Collections.emptyList();
        }
        List<FtmEntity> entities = new ArrayList<>();
        for (Object item : jsonSources) {
            FtmEntity entity = map(item, rule);
            if (entity != null) {
                entities.add(entity);
            }
        }
        return entities;
    }

    /**
     * Evaluates a JsonPath expression, silently ignoring PathNotFoundException or evaluation errors.
     */
    public List<String> extractValuesSilently(DocumentContext documentContext, String jsonPathExpression) {
        try {
            Object result = documentContext.read(jsonPathExpression);
            return flattenToStrings(result);
        } catch (PathNotFoundException e) {
            // Silently ignore missing optional fields as per requirement
            log.trace("JsonPath not found: {} - ignoring silently", jsonPathExpression);
            return Collections.emptyList();
        } catch (Exception e) {
            log.debug("JsonPath evaluation skipped for path: {} ({})", jsonPathExpression, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Generates a deterministic SHA-1 hash for the entity identifier.
     * Evaluates idJsonPaths; if multiple are present, joins them before hashing.
     * If no id value could be found, deterministically hashes the canonical JSON string.
     * Never generates a random UUID.
     */
    public String generateDeterministicId(DocumentContext documentContext, FragmentMappingRule rule) {
        List<String> idValues = new ArrayList<>();

        if (rule.getIdJsonPaths() != null && !rule.getIdJsonPaths().isEmpty()) {
            for (String path : rule.getIdJsonPaths()) {
                List<String> values = extractValuesSilently(documentContext, path);
                for (String val : values) {
                    if (val != null && !val.trim().isEmpty()) {
                        idValues.add(val.trim());
                    }
                }
            }
        }

        String rawIdentifier;
        if (!idValues.isEmpty()) {
            rawIdentifier = String.join(":", idValues);
        } else {
            // Deterministic fallback: hash the canonical JSON of the fragment itself
            rawIdentifier = documentContext.jsonString();
        }

        return computeSha1(rawIdentifier);
    }

    /**
     * Computes a standard SHA-1 hex string.
     */
    public static String computeSha1(String input) {
        if (input == null) {
            return null;
        }
        return DigestUtils.sha1Hex(input.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Recursively flattens any JSON scalar, list, or array into a list of strings.
     */
    private List<String> flattenToStrings(Object value) {
        if (value == null) {
            return Collections.emptyList();
        }

        List<String> results = new ArrayList<>();
        if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                results.addAll(flattenToStrings(item));
            }
        } else if (value.getClass().isArray()) {
            Object[] array = (Object[]) value;
            for (Object item : array) {
                results.addAll(flattenToStrings(item));
            }
        } else if (value instanceof Map<?, ?> map) {
            try {
                results.add(objectMapper.writeValueAsString(map));
            } catch (Exception ignored) {
            }
        } else {
            String str = Objects.toString(value, "").trim();
            if (!str.isEmpty()) {
                results.add(str);
            }
        }
        return results;
    }

    private DocumentContext parseToDocumentContext(Object jsonSource) {
        if (jsonSource instanceof DocumentContext docContext) {
            return docContext;
        }
        if (jsonSource instanceof String jsonString) {
            return JsonPath.using(jsonPathConfig).parse(jsonString);
        }
        return JsonPath.using(jsonPathConfig).parse(jsonSource);
    }
}
