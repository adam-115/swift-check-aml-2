package com.example.app.ingestion;

import com.example.app.ingestion.converter.XmlSourceConverter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ConvertToJsonTest {

    @Test
    void testConvertEuSanctionsXmlToJson() throws Exception {
        XmlSourceConverter xmlConverter = new XmlSourceConverter();
        ObjectMapper objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

        try (InputStream is = getClass().getResourceAsStream("/eu-sanctions-sample.xml")) {
            assertThat(is).as("Le fichier eu-sanctions-sample.xml doit exister").isNotNull();

            List<Map<String, Object>> dynamicJsonRecords = xmlConverter.convertToDynamicJson(is);

            assertThat(dynamicJsonRecords).hasSize(8);

            // Génération du fichier eu-sanctions-sample.json
            File targetJsonFile = new File("src/test/resources/eu-sanctions-sample.json");
            objectMapper.writeValue(targetJsonFile, dynamicJsonRecords);

            assertThat(targetJsonFile).exists();
        }
    }
}
