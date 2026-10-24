package com.example.app.core;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class MappingRuleTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("Devrait discriminer et associer automatiquement chaque entité au bon schéma FTM via conditionJsonPath")
    void testRuleMatchingWithDiscriminator() throws Exception {
        // 1. Chargement de la configuration de mapping EU
        MappingConfig config;
        try (InputStream is = getClass().getResourceAsStream("/eu-sanctions-mapping.json")) {
            assertThat(is).isNotNull();
            config = objectMapper.readValue(is, MappingConfig.class);
        }
        assertThat(config.getRules()).hasSize(5);

        // 2. Chargement des enregistrements convertis
        List<Map<String, Object>> records;
        try (InputStream is = getClass().getResourceAsStream("/eu-sanctions-sample.json")) {
            assertThat(is).isNotNull();
            records = objectMapper.readValue(is, new TypeReference<List<Map<String, Object>>>() {});
        }
        assertThat(records).hasSize(8);

        // Record 0: logicalId="13" -> subjectType.code="person" -> Schema Person
        Optional<MappingRule> personRule = config.findMatchingRule(records.get(0));
        assertThat(personRule).isPresent();
        assertThat(personRule.get().getFtmSchema()).isEqualTo("Person");

        // Record 2: logicalId="88" -> subjectType.code="enterprise" -> Schema Company
        Optional<MappingRule> enterpriseRule = config.findMatchingRule(records.get(2));
        assertThat(enterpriseRule).isPresent();
        assertThat(enterpriseRule.get().getFtmSchema()).isEqualTo("Company");

        // Record 4: logicalId="120" -> subjectType.code="vessel" -> Schema Vessel
        Optional<MappingRule> vesselRule = config.findMatchingRule(records.get(4));
        assertThat(vesselRule).isPresent();
        assertThat(vesselRule.get().getFtmSchema()).isEqualTo("Vessel");

        // Record 5: logicalId="150" -> subjectType.code="aircraft" -> Schema Airplane
        Optional<MappingRule> aircraftRule = config.findMatchingRule(records.get(5));
        assertThat(aircraftRule).isPresent();
        assertThat(aircraftRule.get().getFtmSchema()).isEqualTo("Airplane");

        // Record 6: logicalId="201" -> subjectType.code="sanction" -> Schema Sanction
        Optional<MappingRule> sanctionRule = config.findMatchingRule(records.get(6));
        assertThat(sanctionRule).isPresent();
        assertThat(sanctionRule.get().getFtmSchema()).isEqualTo("Sanction");
    }
}
