package com.example.app.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.app.core.DataSourceConfig;
import com.example.app.ingestion.model.FtmEntity;
import com.example.app.ingestion.model.SourceFormat;
import com.example.app.ingestion.service.FtmGeneratorService;
import com.fasterxml.jackson.databind.ObjectMapper;

class FtmGeneratorServiceTest {

    private final FtmGeneratorService ftmGeneratorService = new FtmGeneratorService();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("Génération FTM de bout en bout via DataSourceConfig avec export NDJSON")
    void testGenerateFtmWithDataSourceConfig(@TempDir Path tempDir) throws Exception {
        Path localSourcePath = tempDir.resolve("input-sanctions.xml");
        Path initialJsonPath = tempDir.resolve("converted-records.json");
        Path ndjsonOutputPath = tempDir.resolve("D:/output-entities.ndjson");

        String euSanctionsUrl = "https://webgate.ec.europa.eu/fsd/fsf/public/files/xmlFullSanctionsList_1_1/content?token=dG9rZW4tMjAxNw";

        DataSourceConfig config = DataSourceConfig.builder()
                .sourceFormat(SourceFormat.XML)
                .externalSourcePath(euSanctionsUrl) // Téléchargement en direct depuis le portail officiel de l'UE
                .localSourcePath(localSourcePath.toString())
                .initialJsonSourcePath(initialJsonPath.toString())
                .mappingFilePath("src/test/resources/eu-sanctions-mapping.json")
                .ftmOutputPath(ndjsonOutputPath.toString())
                .build();

        // Exécution du service
        List<FtmEntity> entities = ftmGeneratorService.generate(config);

        // 1. Vérification que le fichier a été copié / téléchargé dans localSourcePath
        assertThat(localSourcePath).exists();
        assertThat(Files.size(localSourcePath)).isGreaterThan(0);

        // 2. Vérification que le JSON intermédiaire a été généré
        assertThat(initialJsonPath).exists();
        assertThat(Files.size(initialJsonPath)).isGreaterThan(0);

        // 3. Vérification des entités FTM retournées
        assertThat(entities).isNotEmpty();

        // 4. Vérification que le fichier NDJSON a été généré
        assertThat(ndjsonOutputPath).exists();
        List<String> ndjsonLines = Files.readAllLines(ndjsonOutputPath);
        assertThat(ndjsonLines).hasSameSizeAs(entities);

        // Vérifier que chaque ligne est un JSON FTM valide
        for (String line : ndjsonLines) {
            assertThat(line).isNotBlank();
            FtmEntity parsedEntity = objectMapper.readValue(line, FtmEntity.class);
            assertThat(parsedEntity.getId())
                    .as("L'ID doit être un hash SHA-1 valide")
                    .matches("^[a-f0-9]{40}$");
            assertThat(parsedEntity.getSchema()).isNotBlank();
            assertThat(parsedEntity.getProperties()).isNotNull();
        }
    }
}
