package com.example.app.execution;

import com.example.app.api.ConflictException;
import com.example.app.api.NotFoundException;
import com.example.app.core.DataSourceConfig;
import com.example.app.core.ListType;
import com.example.app.core.repository.DataSourceConfigRepository;
import com.example.app.ingestion.IngestionException;
import com.example.app.ingestion.model.SourceFormat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class ExecutionServiceTest {

    @Autowired
    private ExecutionService executionService;

    @Autowired
    private DataSourceConfigRepository sourceRepository;

    private DataSourceConfig source(String name, String mapping, Path dir) {
        return sourceRepository.save(DataSourceConfig.builder()
                .sourceName(name)
                .label("Test " + name)
                .sourceFormat(SourceFormat.CSV)
                .listType(ListType.PEP)
                .converterOptions("{\"encoding\": \"windows-1252\"}")
                .externalSourcePath("src/test/resources/fixtures/lu/lu_pep_chamber/chamber-current-deputies.csv")
                .localSourcePath(dir.resolve("source.csv").toString())
                .initialJsonSourcePath(dir.resolve("records.json").toString())
                .mappingFilePath(mapping)
                .ftmOutputPath(dir.resolve("out.ftm.json").toString())
                .build());
    }

    @Test
    @DisplayName("Une exécution réussie enregistre les chiffres de chaque étape et le fichier produit")
    void successfulExecution(@TempDir Path dir) throws Exception {
        DataSourceConfig source = source("test_exec_ok", "classpath:mappings/lu/lu_pep_chamber.mapping.json", dir);

        SourceExecution execution = executionService.runNow(source.getId());

        assertThat(execution.getStatus()).isEqualTo(ExecutionStatus.SUCCESS);
        assertThat(execution.getSourceRecords()).isEqualTo(2);
        assertThat(execution.getPersonCount()).isEqualTo(2);
        assertThat(execution.getEntityCount()).isEqualTo(5);
        assertThat(execution.getEntitiesBySchema()).contains("\"Occupancy\":2");
        assertThat(execution.getFinishedAt()).isNotNull();
        assertThat(Files.readAllLines(dir.resolve("out.ftm.json"))).hasSize(5);
    }

    @Test
    @DisplayName("Un mapping introuvable donne une exécution en échec à l'étape CONFIGURATION, avec message et trace")
    void failedExecutionRecordsStepAndMessage(@TempDir Path dir) {
        DataSourceConfig source = source("test_exec_ko", "classpath:mappings/inexistant.mapping.json", dir);

        SourceExecution execution = executionService.runNow(source.getId());

        assertThat(execution.getStatus()).isEqualTo(ExecutionStatus.FAILED);
        assertThat(execution.getFailedStep()).isEqualTo(IngestionException.Step.CONFIGURATION);
        assertThat(execution.getErrorMessage()).contains("Fichier de mapping introuvable");
        assertThat(execution.getErrorDetail()).contains("IngestionException");
        assertThat(execution.getFinishedAt()).isNotNull();
    }

    @Test
    @DisplayName("Un fichier source absent donne un échec à l'étape DOWNLOAD")
    void missingSourceFile(@TempDir Path dir) {
        DataSourceConfig source = source("test_exec_nofile", "classpath:mappings/lu/lu_pep_chamber.mapping.json", dir);
        source.setExternalSourcePath("src/test/resources/fixtures/absent.csv");
        sourceRepository.save(source);

        SourceExecution execution = executionService.runNow(source.getId());

        assertThat(execution.getStatus()).isEqualTo(ExecutionStatus.FAILED);
        assertThat(execution.getFailedStep()).isEqualTo(IngestionException.Step.DOWNLOAD);
        assertThat(execution.getErrorMessage()).contains("absent.csv");
    }

    @Test
    @DisplayName("Source inconnue : NotFoundException ; source désactivée ou sans mapping : ConflictException")
    void refusedExecutions(@TempDir Path dir) {
        assertThatThrownBy(() -> executionService.runNow(999_999L)).isInstanceOf(NotFoundException.class);

        DataSourceConfig disabled = source("test_exec_disabled", "classpath:mappings/lu/lu_pep_chamber.mapping.json", dir);
        disabled.setEnabled(false);
        sourceRepository.save(disabled);
        assertThatThrownBy(() -> executionService.runNow(disabled.getId()))
                .isInstanceOf(ConflictException.class).hasMessageContaining("désactivée");

        DataSourceConfig noMapping = source("test_exec_nomapping", null, dir);
        assertThatThrownBy(() -> executionService.runNow(noMapping.getId()))
                .isInstanceOf(ConflictException.class).hasMessageContaining("mapping");
    }
}
