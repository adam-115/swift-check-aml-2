package com.example.app.ingestion;

import com.example.app.core.entities.DataSourceConfig;
import com.example.app.ingestion.model.FtmEntity;
import com.example.app.ingestion.model.SourceFormat;
import com.example.app.ingestion.service.FtmGeneratorService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Source lu_pep_diplomats : le fichier unique data/lu/lu_pep_diplomats.json et son mapping donnent, pour chaque chef
 * de mission, une Person, une Position et l'Occupancy qui les relie.
 */
class LuPepDiplomatsMappingTest {

    @Test
    @DisplayName("lu_pep_diplomats : Person + Position + Occupancy reliées pour chaque chef de mission")
    void mapsHeadsOfMission(@TempDir Path tempDir) throws Exception {
        DataSourceConfig config = DataSourceConfig.builder()
                .sourceName("lu_pep_diplomats")
                .sourceFormat(SourceFormat.JSON)
                .localSourcePath("classpath:data/lu/lu_pep_diplomats.json")
                .initialJsonSourcePath(tempDir.resolve("records.json").toString())
                .mappingFilePath("classpath:mappings/lu/lu_pep_diplomats.mapping.json")
                .ftmOutputPath(tempDir.resolve("lu_pep_diplomats.ftm.json").toString())
                .build();

        List<FtmEntity> entities = new FtmGeneratorService().generate(config);

        Map<String, List<FtmEntity>> bySchema = entities.stream().collect(Collectors.groupingBy(FtmEntity::getSchema));
        List<FtmEntity> occupancies = bySchema.get("Occupancy");
        assertThat(occupancies).hasSizeGreaterThanOrEqualTo(30);
        assertThat(bySchema.get("Position")).hasSameSizeAs(occupancies);

        // Un ambassadeur non résident accrédité dans plusieurs pays reste une seule personne.
        long distinctPersons = bySchema.get("Person").stream().map(FtmEntity::getId).distinct().count();
        assertThat(distinctPersons).isLessThan(occupancies.size());

        FtmEntity paris = bySchema.get("Person").stream()
                .filter(p -> p.getProperties().get("sourceUrl").contains("https://paris.mae.lu/fr/ambassades/equipe.html"))
                .findFirst().orElseThrow();
        assertThat(paris.getProperties().get("topics")).containsExactly("role.pep");
        assertThat(paris.getProperties().get("country")).containsExactly("LU");

        // Chaque Occupancy pointe vers une Person et une Position générées.
        var ids = entities.stream().map(FtmEntity::getId).collect(Collectors.toSet());
        for (FtmEntity occupancy : occupancies) {
            assertThat(ids).contains(occupancy.getProperties().get("holder").get(0), occupancy.getProperties().get("post").get(0));
            assertThat(occupancy.getProperties().get("status")).containsExactly("current");
        }

        assertThat(Files.readAllLines(tempDir.resolve("lu_pep_diplomats.ftm.json"))).hasSize(entities.size());
    }
}
