package com.example.app.ingestion.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Output entity matching the FollowTheMoney (FTM) ontology specification.
 * In FTM:
 * - 'id' is a deterministic string identifier.
 * - 'schema' is the ontology schema (e.g., Person, Organization, Sanction, Address).
 * - 'properties' maps property names to lists of string values.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FtmEntity {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private String id;
    private String schema;

    @Builder.Default
    private Map<String, List<String>> properties = new LinkedHashMap<>();

    /**
     * Adds a single value to a property list.
     */
    public void addProperty(String key, String value) {
        if (key == null || value == null || value.trim().isEmpty()) {
            return;
        }
        if (properties == null) {
            properties = new LinkedHashMap<>();
        }
        properties.computeIfAbsent(key, k -> new ArrayList<>()).add(value);
    }

    /**
     * Adds multiple values to a property list.
     */
    public void addProperties(String key, Collection<String> values) {
        if (key == null || values == null || values.isEmpty()) {
            return;
        }
        for (String val : values) {
            addProperty(key, val);
        }
    }

    /**
     * Serializes this entity into a single-line JSON string suitable for NDJSON streams.
     */
    public String toJsonString() {
        try {
            return MAPPER.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize FtmEntity to NDJSON line", e);
        }
    }
}
