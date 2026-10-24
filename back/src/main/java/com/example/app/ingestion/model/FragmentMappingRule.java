package com.example.app.ingestion.model;

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
 * Configuration rule defining how a raw dynamic JSON fragment is mapped
 * to a FollowTheMoney (FTM) ontological entity.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FragmentMappingRule {

    /**
     * Target source node name (e.g. "Person", "sdnEntry", "sanctionEntity", "row").
     */
    private String targetNodeName;

    /**
     * Optional discriminator JsonPath condition (e.g. "$.subjectType.code", "$.sdnType").
     */
    private String conditionJsonPath;

    /**
     * Expected value of the discriminator (e.g. "person", "enterprise", "vessel").
     */
    private String conditionValue;

    /**
     * FollowTheMoney schema name (e.g. "Person", "Organization", "Sanction", "Address", "Vessel").
     */
    private String ftmSchema;

    /**
     * List of JsonPath expressions used to extract composite or primary IDs.
     * The extracted values will be concatenated and hashed using SHA-1.
     */
    @Builder.Default
    private List<String> idJsonPaths = new ArrayList<>();

    /**
     * Mapping of FTM property names to their corresponding JsonPath expressions.
     * Example: "name" -> "$.firstName", "birthDate" -> "$.dateOfBirth"
     */
    @Builder.Default
    private Map<String, String> propertiesMapping = new LinkedHashMap<>();

    /**
     * List of FTM property names whose extracted values represent foreign keys
     * (references to other entities). Values for these properties are automatically
     * hashed with SHA-1 to match the deterministic IDs of target entities.
     */
    @Builder.Default
    private List<String> foreignKeyProperties = new ArrayList<>();

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
            String actual = String.valueOf(val).trim();
            return conditionValue != null && conditionValue.equalsIgnoreCase(actual);
        } catch (Exception e) {
            return false;
        }
    }

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
