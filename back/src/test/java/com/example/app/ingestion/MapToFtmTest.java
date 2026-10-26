package com.example.app.ingestion;

import com.example.app.ingestion.engine.DynamicFtmMapper;
import com.example.app.ingestion.model.FtmEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.InputStream;
import java.util.List;

class MapToFtmTest {

    private final ObjectMapper objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private final DynamicFtmMapper dynamicFtmMapper = new DynamicFtmMapper();

    /**
     * Fonction de test prenant en entrée le fichier source JSON et le fichier de mapping JSON,
     * et retournant la liste des FtmEntity via le service DynamicFtmMapper (avec identifiants et clés étrangères hachés en SHA-1).
     */
    @Test
    @DisplayName("Prend en input eu-sanctions-sample.json et eu-sanctions-mapping.json et retourne List<FtmEntity> avec IDs hachés")
    void testMapEuSanctionsJsonToFtmEntities() throws Exception {
        List<FtmEntity> ftmEntities;

        try (InputStream inputStream = getClass().getResourceAsStream("/eu-sanctions-sample.json");
             InputStream mappingIstream = getClass().getResourceAsStream("/eu-sanctions-mapping.json")) {

            ftmEntities = dynamicFtmMapper.map(inputStream, mappingIstream);
        }

        // Écriture du fichier de sortie FTM JSON pour inspection visuelle
        File targetOutput = new File("src/test/resources/eu-sanctions-mapped-ftm.json");
        objectMapper.writeValue(targetOutput, ftmEntities);
    }

    @Test
    @DisplayName("Teste les constantes directes, les identifiants automatiques et les relations sans RecordPreprocessor")
    void testDirectConstantsAndAutomaticRelations() {
        com.example.app.core.MappingConfig config = com.example.app.core.MappingConfig.builder()
                .name("Test Smart Mapping")
                .applyAllRules(true)
                .rules(List.of(
                        com.example.app.core.MappingRule.builder()
                                .ftmSchema("Person")
                                .propertiesMapping(java.util.Map.of(
                                        "name", "$.name",
                                        "country", "lu",
                                        "topics", "role.pep"
                                ))
                                .propertyTypes(java.util.Map.of("country", "country"))
                                .build(),
                        com.example.app.core.MappingRule.builder()
                                .ftmSchema("Position")
                                .propertiesMapping(java.util.Map.of(
                                        "name", "Membre de la Commission européenne",
                                        "country", "lu"
                                ))
                                .propertyTypes(java.util.Map.of("country", "country"))
                                .build(),
                        com.example.app.core.MappingRule.builder()
                                .ftmSchema("Occupancy")
                                .propertiesMapping(java.util.Map.of(
                                        "holder", "Person",
                                        "post", "Position",
                                        "startDate", "2024-12-01",
                                        "status", "current"
                                ))
                                .propertyTypes(java.util.Map.of("holder", "entity", "post", "entity", "startDate", "date"))
                                .build()
                ))
                .build();

        List<java.util.Map<String, Object>> records = List.of(
                java.util.Map.of("name", "Christophe Hansen", "description", "Commissaire européen")
        );

        List<FtmEntity> entities = dynamicFtmMapper.map(records, config);

        org.junit.jupiter.api.Assertions.assertEquals(3, entities.size());

        FtmEntity person = entities.stream().filter(e -> "Person".equals(e.getSchema())).findFirst().orElseThrow();
        org.junit.jupiter.api.Assertions.assertNotNull(person.getId());
        org.junit.jupiter.api.Assertions.assertEquals("Christophe Hansen", person.getProperties().get("name").get(0));
        org.junit.jupiter.api.Assertions.assertEquals("LU", person.getProperties().get("country").get(0));
        org.junit.jupiter.api.Assertions.assertEquals("role.pep", person.getProperties().get("topics").get(0));

        FtmEntity position = entities.stream().filter(e -> "Position".equals(e.getSchema())).findFirst().orElseThrow();
        org.junit.jupiter.api.Assertions.assertNotNull(position.getId());
        org.junit.jupiter.api.Assertions.assertEquals("Membre de la Commission européenne", position.getProperties().get("name").get(0));
        org.junit.jupiter.api.Assertions.assertEquals("LU", position.getProperties().get("country").get(0));

        FtmEntity occupancy = entities.stream().filter(e -> "Occupancy".equals(e.getSchema())).findFirst().orElseThrow();
        org.junit.jupiter.api.Assertions.assertNotNull(occupancy.getId());
        // Vérifie que holder pointe exactement vers l'ID de la Person
        org.junit.jupiter.api.Assertions.assertEquals(person.getId(), occupancy.getProperties().get("holder").get(0));
        // Vérifie que post pointe exactement vers l'ID de la Position
        org.junit.jupiter.api.Assertions.assertEquals(position.getId(), occupancy.getProperties().get("post").get(0));
        org.junit.jupiter.api.Assertions.assertEquals("2024-12-01", occupancy.getProperties().get("startDate").get(0));
        org.junit.jupiter.api.Assertions.assertEquals("current", occupancy.getProperties().get("status").get(0));
    }

    @Test
    @DisplayName("Teste la concaténation de colonnes via template {$.first_name} {$.last_name}")
    void testTemplateConcatenation() {
        com.example.app.core.MappingConfig config = com.example.app.core.MappingConfig.builder()
                .name("Test Template Concatenation")
                .rules(List.of(
                        com.example.app.core.MappingRule.builder()
                                .ftmSchema("Person")
                                .propertiesMapping(java.util.Map.of(
                                        "name", "{$.prenom} {$.nom}",
                                        "firstName", "$.prenom",
                                        "lastName", "$.nom",
                                        "country", "LU"
                                ))
                                .propertyTypes(java.util.Map.of("country", "country"))
                                .build()
                ))
                .build();

        List<java.util.Map<String, Object>> records = List.of(
                java.util.Map.of("prenom", "Jean", "nom", "Asselborn")
        );

        List<FtmEntity> entities = dynamicFtmMapper.map(records, config);

        org.junit.jupiter.api.Assertions.assertEquals(1, entities.size());
        FtmEntity person = entities.get(0);
        org.junit.jupiter.api.Assertions.assertEquals("Jean Asselborn", person.getProperties().get("name").get(0));
        org.junit.jupiter.api.Assertions.assertEquals("Jean", person.getProperties().get("firstName").get(0));
        org.junit.jupiter.api.Assertions.assertEquals("Asselborn", person.getProperties().get("lastName").get(0));
    }
}
