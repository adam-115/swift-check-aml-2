package com.example.app.catalog;

import com.example.app.core.DataSourceConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Lit les définitions de sources du catalogue et en fait des {@link DataSourceConfig}.
 */
public final class SourceCatalog {

    public static final String LOCATION = "classpath*:catalog/**/*.source.json";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private SourceCatalog() {
    }

    public static List<SourceDefinition> load() {
        try {
            Resource[] resources = new PathMatchingResourcePatternResolver().getResources(LOCATION);
            List<SourceDefinition> definitions = new ArrayList<>();
            for (Resource resource : resources) {
                try (InputStream is = resource.getInputStream()) {
                    definitions.add(MAPPER.readValue(is, SourceDefinition.class));
                } catch (IOException e) {
                    throw new IllegalStateException("Définition de source illisible : " + resource.getDescription()
                            + " (" + e.getMessage() + ")", e);
                }
            }
            definitions.sort(Comparator.comparing(SourceDefinition::sourceName));
            return definitions;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Applique la définition à une configuration (nouvelle ou existante). L'activation et l'adresse ne sont
     * posées qu'à la création ou lors d'une réinitialisation (source non personnalisée).
     *
     * @param dataDir dossier de travail (fichiers téléchargés, JSON intermédiaire, sorties FTM)
     */
    public static void apply(SourceDefinition d, DataSourceConfig config, String dataDir) {
        boolean created = config.getId() == null;
        config.setSourceName(d.sourceName());
        config.setLabel(d.label());
        config.setDescription(d.description());
        config.setAttribution(d.attribution());
        config.setListType(d.listType());
        config.setParserType(d.parserType());
        config.setSourceFormat(d.sourceFormat());
        config.setRetentionMonths(d.retentionMonths());
        config.setMappingFilePath(d.mapping());
        config.setConverterOptions(d.converterOptions() == null || d.converterOptions().isNull()
                ? null : d.converterOptions().toString());
        String extension = d.sourceFormat() == null ? "dat" : d.sourceFormat().name().toLowerCase();
        String base = dataDir.replace('\\', '/').replaceAll("/+$", "");
        config.setLocalSourcePath(base + "/sources/" + d.sourceName() + "/source." + extension);
        config.setInitialJsonSourcePath(base + "/sources/" + d.sourceName() + "/records.json");
        config.setFtmOutputPath(base + "/ftm/" + d.sourceName() + ".ftm.json");
        config.setExternalSourcePath(d.urls() == null ? null : String.join("\n", d.urls()));
        config.setCustomized(false);
        if (created) {
            config.setEnabled(true);
        }
    }
}
