package com.example.app.catalog;

import com.example.app.core.DataSourceConfig;
import com.example.app.ingestion.IngestionException;
import com.example.app.ingestion.service.FtmGeneratorService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.nio.file.Path;
import java.util.stream.Collectors;

/**
 * Outil de mise au point d'un mapping, lancé à la demande :
 * {@code mvn test -Dtest=SourceCatalogDumpTest -Ddump=lu_pep_chamber}.
 * Écrit dans {@code target/dump/<source>/} le JSON converti ({@code records.json}) et le fichier FTM.
 * {@code -Ddump.source=<chemin>} remplace les fichiers de test par un autre fichier source.
 */
@EnabledIfSystemProperty(named = "dump", matches = ".+")
class SourceCatalogDumpTest {

    @Test
    void dump() {
        String name = System.getProperty("dump");
        SourceDefinition d = SourceCatalog.load().stream()
                .filter(s -> s.sourceName().equals(name))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Source inconnue : " + name));
        DataSourceConfig config = new DataSourceConfig();
        SourceCatalog.apply(d, config, Path.of("target/dump").toString());
        config.setExternalSourcePath(System.getProperty("dump.source", d.test().fixtures().stream()
                .map(f -> "src/test/resources/fixtures/" + d.country().toLowerCase() + "/" + d.sourceName() + "/" + f)
                .collect(Collectors.joining("\n"))));
        if (d.mapping() == null) {
            config.setMappingFilePath("classpath:mappings/dump-only.mapping.json");
        }
        try {
            var report = new FtmGeneratorService().run(config);
            System.out.println("DUMP " + name + " : " + report.sourceRecords() + " enregistrements, "
                    + report.mappedRecords() + " gardés, " + report.entitiesBySchema());
        } catch (IngestionException e) {
            System.out.println("DUMP " + name + " : " + e.getStep() + " - " + e.getMessage());
        }
    }
}
