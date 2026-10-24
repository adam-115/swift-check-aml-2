package com.example.app.ingestion.config;

import com.example.app.ingestion.model.FragmentMappingRule;
import com.example.app.ingestion.model.IngestionConfig;

import java.util.List;
import java.util.Map;

/**
 * Built-in standard AML/KYC sanction mapping presets.
 */
public final class IngestionRulePresets {

    private IngestionRulePresets() {}

    /**
     * OFAC SDN XML fragment mapping preset:
     * Transforms <sdnEntry> XML into FollowTheMoney "Person" or "LegalEntity" entity.
     */
    public static FragmentMappingRule ofacSdnPersonRule() {
        return FragmentMappingRule.builder()
                .targetNodeName("sdnEntry")
                .ftmSchema("Person")
                .idJsonPaths(List.of("$.uid"))
                .propertiesMapping(Map.of(
                        "name", "$.lastName",
                        "firstName", "$.firstName",
                        "notes", "$.remarks",
                        "title", "$.title",
                        "program", "$.programList.program[*]"
                ))
                .foreignKeyProperties(List.of("sanction", "holder"))
                .build();
    }

    /**
     * OFAC Sanctions Link mapping rule:
     * Demonstrates foreignKeyProperties hashing for relational integrity.
     */
    public static FragmentMappingRule ofacSanctionRule() {
        return FragmentMappingRule.builder()
                .targetNodeName("sanction")
                .ftmSchema("Sanction")
                .idJsonPaths(List.of("$.uid", "$.program"))
                .propertiesMapping(Map.of(
                        "entity", "$.uid", // Points to Person.uid -> will be SHA-1 hashed!
                        "program", "$.program",
                        "authority", "$.authority"
                ))
                .foreignKeyProperties(List.of("entity")) // Auto-hashed to match Person deterministic ID!
                .build();
    }

    /**
     * CSV Sanctions mapping rule.
     */
    public static FragmentMappingRule csvSanctionsRule() {
        return FragmentMappingRule.builder()
                .targetNodeName("csvRecord")
                .ftmSchema("Person")
                .idJsonPaths(List.of("$.Entity_ID"))
                .propertiesMapping(Map.of(
                        "name", "$.Name",
                        "country", "$.Country",
                        "birthDate", "$.DOB",
                        "sanction", "$.Sanction_ID"
                ))
                .foreignKeyProperties(List.of("sanction"))
                .build();
    }

    public static IngestionConfig standardConfig() {
        return IngestionConfig.builder()
                .name("Standard AML Sanctions Ingestion Config")
                .description("Default rules for OFAC XML, CSV and EU JSON sanction lists")
                .rules(List.of(ofacSdnPersonRule(), ofacSanctionRule(), csvSanctionsRule()))
                .build();
    }
}
