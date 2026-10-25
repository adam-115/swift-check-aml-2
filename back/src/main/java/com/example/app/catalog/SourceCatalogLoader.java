package com.example.app.catalog;

import com.example.app.core.DataSourceConfig;
import com.example.app.core.ParserCoverage;
import com.example.app.core.pep.Country;
import com.example.app.core.pep.PepFunction;
import com.example.app.core.repository.CountryRepository;
import com.example.app.core.repository.DataSourceConfigRepository;
import com.example.app.core.repository.ParserCoverageRepository;
import com.example.app.core.repository.PepFunctionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Au démarrage, crée ou met à jour les sources du catalogue et leur couverture PEP.
 * Une source modifiée ou supprimée depuis l'interface n'est plus touchée ; elle peut être réinitialisée.
 */
@Slf4j
@Component
public class SourceCatalogLoader {

    private final DataSourceConfigRepository sourceRepository;
    private final ParserCoverageRepository coverageRepository;
    private final PepFunctionRepository functionRepository;
    private final CountryRepository countryRepository;
    private final boolean enabled;
    private final String dataDir;
    private List<SourceDefinition> definitions;

    public SourceCatalogLoader(DataSourceConfigRepository sourceRepository,
                               ParserCoverageRepository coverageRepository,
                               PepFunctionRepository functionRepository,
                               CountryRepository countryRepository,
                               @Value("${app.catalog.enabled:true}") boolean enabled,
                               @Value("${app.data-dir:data}") String dataDir) {
        this.sourceRepository = sourceRepository;
        this.coverageRepository = coverageRepository;
        this.functionRepository = functionRepository;
        this.countryRepository = countryRepository;
        this.enabled = enabled;
        this.dataDir = dataDir;
    }

    @Order(0)
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void load() {
        if (!enabled) {
            return;
        }
        List<SourceDefinition> definitions = definitions();
        for (SourceDefinition definition : definitions) {
            sync(definition);
        }
        log.info("Catalogue des sources chargé : {} source(s)", definitions.size());
    }

    /**
     * Crée ou met à jour la source. Une source modifiée ou supprimée depuis l'interface n'est pas touchée.
     */
    void sync(SourceDefinition d) {
        DataSourceConfig existing = sourceRepository.findBySourceName(d.sourceName()).orElse(null);
        if (existing != null && (existing.isCustomized() || existing.getDeletedAt() != null)) {
            return;
        }
        apply(d, existing != null ? existing : new DataSourceConfig());
    }

    /** Définition du catalogue pour ce nom de source, s'il y en a une. */
    public Optional<SourceDefinition> definition(String sourceName) {
        return definitions().stream().filter(d -> d.sourceName().equals(sourceName)).findFirst();
    }

    /** Noms des sources définies dans le catalogue. */
    public Set<String> catalogNames() {
        return definitions().stream().map(SourceDefinition::sourceName).collect(Collectors.toSet());
    }

    /**
     * Remet une source dans l'état de sa définition du catalogue (adresse, mapping, couverture...) ;
     * l'activation est gardée. Elle suit de nouveau le catalogue aux démarrages suivants.
     */
    @Transactional
    public DataSourceConfig reset(DataSourceConfig config) {
        SourceDefinition d = definition(config.getSourceName())
                .orElseThrow(() -> new IllegalArgumentException("La source « " + config.getSourceName()
                        + " » n'est pas définie dans le catalogue : elle ne peut pas être réinitialisée."));
        return apply(d, config);
    }

    private synchronized List<SourceDefinition> definitions() {
        if (definitions == null) {
            definitions = SourceCatalog.load();
        }
        return definitions;
    }

    private DataSourceConfig apply(SourceDefinition d, DataSourceConfig config) {
        SourceCatalog.apply(d, config, dataDir);
        Country country = d.country() == null ? null : countryRepository.findById(d.country())
                .orElseThrow(() -> new IllegalStateException("Pays inconnu " + d.country() + " pour " + d.sourceName()));
        config.setCountry(country);
        DataSourceConfig saved = sourceRepository.save(config);

        coverageRepository.deleteAll(coverageRepository.findByDataSourceConfigId(saved.getId()));
        coverageRepository.flush();
        if (d.coverage() == null) {
            return saved;
        }
        for (SourceDefinition.Coverage c : d.coverage()) {
            PepFunction function = functionRepository.findByCategoryCodeAndLabel(c.category(), c.function())
                    .orElseThrow(() -> new IllegalStateException("Fonction PEP inconnue « " + c.function()
                            + " » (" + c.category() + ") dans la définition de " + d.sourceName()));
            coverageRepository.save(ParserCoverage.builder()
                    .dataSourceConfig(saved)
                    .pepFunction(function)
                    .completeness(c.completeness())
                    .includesFormer(c.includesFormer())
                    .build());
        }
        return saved;
    }
}
