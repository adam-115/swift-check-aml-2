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
}
