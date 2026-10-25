package com.example.app.catalog;

import com.example.app.core.DataSourceConfig;
import com.example.app.ingestion.model.FtmEntity;
import com.example.app.ingestion.model.GenerationReport;
import com.example.app.ingestion.service.FtmGeneratorService;
import tech.followthemoney.model.Model;
import tech.followthemoney.model.Property;
import tech.followthemoney.model.Schema;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exécute chaque source implémentée du catalogue sur ses fichiers de test hors ligne
 * ({@code src/test/resources/fixtures/<pays>/<source>/}) et vérifie le fichier FTM produit :
 * nombre de personnes, noms attendus, identifiants SHA-1, et liens entre entités résolus.
 */
class SourceCatalogFixtureTest {

    /** Propriétés FTM qui désignent une autre entité. */
    private static final Set<String> ENTITY_PROPERTIES = Set.of("holder", "post", "person", "relative", "owner",
            "asset", "organization", "member", "entity");

    private static final Model FTM_MODEL = loadModel();

    private final FtmGeneratorService service = new FtmGeneratorService();

    private static Model loadModel() {
        try {
            return Model.loadDefault();
        } catch (Exception e) {
            throw new IllegalStateException("Modèle FollowTheMoney illisible", e);
        }
    }

    /**
     * Schéma et propriétés connus du modèle FtM officiel, valeurs au format de leur type
     * (dates ISO, codes pays, références vers des entités du lot), propriétés obligatoires présentes.
     */
    private static void assertConformsToFtmModel(String source, FtmEntity e, Map<String, FtmEntity> byId) {
        Schema schema = FTM_MODEL.getSchema(e.getSchema());
        assertThat(schema).as("schéma FtM %s (%s)", e.getSchema(), source).isNotNull();
        for (Property required : schema.getRequiredProperties()) {
            assertThat(e.getProperties()).as("%s.%s obligatoire (%s)", e.getSchema(), required.getName(), source)
                    .containsKey(required.getName());
        }
        e.getProperties().forEach((name, values) -> {
            assertThat(schema.hasProperty(name)).as("propriété %s.%s inconnue du modèle FtM (%s)",
                    e.getSchema(), name, source).isTrue();
            String type = schema.getProperty(name).getType().getName();
            for (String value : values) {
                switch (type) {
                    case "date" -> assertThat(value).as("%s.%s = %s (%s)", e.getSchema(), name, value, source)
                            .matches("^\\d{4}(-\\d{2}(-\\d{2})?)?$");
                    case "country" -> assertThat(value).as("%s.%s = %s (%s)", e.getSchema(), name, value, source)
                            .matches("^[A-Za-z]{2}$");
                    case "entity" -> assertThat(byId).as("%s.%s pointe vers une entité absente (%s)",
                            e.getSchema(), name, source).containsKey(value);
                    case "gender" -> assertThat(value).isIn("male", "female", "other");
                    default -> assertThat(value).as("%s.%s (%s)", e.getSchema(), name, source).isNotBlank();
                }
            }
        });
    }

    static Stream<SourceDefinition> implementedSources() {
        return SourceCatalog.load().stream().filter(d -> d.mapping() != null);
    }

    @Test
    @DisplayName("Le catalogue contient les 24 sources PEP du Luxembourg, chacune avec un fichier de test")
    void catalogIsComplete() {
        List<SourceDefinition> all = SourceCatalog.load();
        assertThat(all.stream().filter(d -> "LU".equals(d.country())).count()).isEqualTo(24);
        assertThat(all).extracting(SourceDefinition::sourceName).doesNotHaveDuplicates();
        for (SourceDefinition d : all) {
            assertThat(d.coverage()).as("couverture de %s", d.sourceName()).isNotEmpty();
            if (d.mapping() != null) {
                assertThat(d.test()).as("test hors ligne de %s", d.sourceName()).isNotNull();
                assertThat(d.test().fixtures()).as("fichiers de test de %s", d.sourceName()).isNotEmpty();
            }
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("implementedSources")
    void generatesValidFtm(SourceDefinition d, @TempDir Path tempDir) throws Exception {
        DataSourceConfig config = new DataSourceConfig();
        SourceCatalog.apply(d, config, tempDir.toString());
        config.setExternalSourcePath(d.test().fixtures().stream()
                .map(f -> "src/test/resources/fixtures/" + d.country().toLowerCase() + "/" + d.sourceName() + "/" + f)
                .collect(Collectors.joining("\n")));

        GenerationReport report = service.run(config);
        List<FtmEntity> entities = report.entities();

        assertThat(Files.readAllLines(Path.of(config.getFtmOutputPath()))).hasSameSizeAs(entities);
        assertThat(report.count("Person")).as("personnes de %s", d.sourceName())
                .isGreaterThanOrEqualTo(d.test().minPersons() == null ? 1 : d.test().minPersons());

        Map<String, FtmEntity> byId = FtmGeneratorService.byId(entities);
        for (FtmEntity e : entities) {
            assertThat(e.getId()).matches("^[a-f0-9]{40}$");
            assertConformsToFtmModel(d.sourceName(), e, byId);
            assertThat(e.getProperties()).as("propriétés de %s %s", e.getSchema(), e.getId()).isNotEmpty();
            if (Set.of("Person", "Position", "Organization").contains(e.getSchema())) {
                assertThat(e.getProperties().get("name")).as("nom de %s %s", e.getSchema(), e.getProperties())
                        .isNotEmpty();
            }
            e.getProperties().forEach((property, values) -> {
                if (ENTITY_PROPERTIES.contains(property)) {
                    for (String ref : values) {
                        assertThat(byId).as("%s.%s de %s pointe vers une entité absente", e.getSchema(), property,
                                d.sourceName()).containsKey(ref);
                    }
                }
            });
            if ("Occupancy".equals(e.getSchema())) {
                assertThat(e.getProperties()).containsKeys("holder", "post");
            }
        }

        assertThat(entities.stream().filter(e -> "Occupancy".equals(e.getSchema())).count())
                .as("mandats de %s", d.sourceName()).isGreaterThanOrEqualTo(report.count("Person"));

        if (d.test().samples() != null) {
            Set<String> names = entities.stream()
                    .filter(e -> "Person".equals(e.getSchema()))
                    .flatMap(e -> e.getProperties().getOrDefault("name", List.of()).stream())
                    .collect(Collectors.toSet());
            assertThat(names).as("noms publiés par %s", d.sourceName()).containsAll(d.test().samples());
        }
    }
}
