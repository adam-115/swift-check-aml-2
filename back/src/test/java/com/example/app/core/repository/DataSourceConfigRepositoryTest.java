package com.example.app.core.repository;

import com.example.app.core.DataSourceConfig;
import com.example.app.ingestion.model.SourceFormat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class DataSourceConfigRepositoryTest {

    @Autowired
    private DataSourceConfigRepository repository;

    @Test
    @DisplayName("Persistance et récupération JPA d'une configuration DataSourceConfig")
    void testSaveAndFindDataSourceConfig() {
        DataSourceConfig config = DataSourceConfig.builder()
                .sourceName("EU-SANCTIONS")
                .sourceFormat(SourceFormat.XML)
                .externalSourcePath("https://webgate.ec.europa.eu/fsd/fsf/public/files/xmlFullSanctionsList_1_1/content?token=dG9rZW4tMjAxNw")
                .localSourcePath("data/eu-sanctions.xml")
                .initialJsonSourcePath("data/eu-sanctions.json")
                .mappingFilePath("mappings/eu-sanctions-mapping.json")
                .ftmOutputPath("exports/eu-sanctions.ndjson")
                .build();

        DataSourceConfig saved = repository.save(config);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();

        Optional<DataSourceConfig> found = repository.findBySourceName("EU-SANCTIONS");
        assertThat(found).isPresent();
        assertThat(found.get().getSourceFormat()).isEqualTo(SourceFormat.XML);
        assertThat(found.get().getExternalSourcePath()).contains("webgate.ec.europa.eu");
    }
}
