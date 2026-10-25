package com.example.app.ingestion.converter;

import com.example.app.ingestion.model.SourceFormat;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Dynamically converts raw JSON payloads into dynamic JSON Maps.
 */
@Slf4j
@Component
public class JsonSourceConverter implements IDynamicSourceConverter {

    private final ObjectMapper objectMapper;

    public JsonSourceConverter() {
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public SourceFormat getSupportedFormat() {
        return SourceFormat.JSON;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> convertToDynamicJson(InputStream inputStream) throws Exception {
        JsonNode rootNode = objectMapper.readTree(inputStream);
        if (rootNode == null || rootNode.isEmpty()) {
            return Collections.emptyList();
        }

        if (rootNode.isArray()) {
            return objectMapper.convertValue(rootNode, new TypeReference<List<Map<String, Object>>>() {
            });
        }

        if (rootNode.isObject()) {
            Map<String, Object> map = objectMapper.convertValue(rootNode, new TypeReference<Map<String, Object>>() {
            });
            // Check for common collection envelopes like "results", "data", "records",
            // "items", "entities"
            for (String key : List.of("results", "data", "records", "items", "list", "entities", "entites", "sanctions")) {
                Object val = map.get(key);
                if (val instanceof List<?> list) {
                    List<Map<String, Object>> records = new ArrayList<>();
                    for (Object item : list) {
                        if (item instanceof Map<?, ?> itemMap) {
                            records.add((Map<String, Object>) itemMap);
                        }
                    }
                    if (!records.isEmpty()) {
                        return records;
                    }
                }
            }
            return List.of(map);
        }

        return Collections.emptyList();
    }
}
