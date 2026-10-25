package com.example.app.api;

import com.example.app.api.dto.SourceRequest;
import com.example.app.catalog.SourceCatalogLoader;
import com.example.app.core.DataSourceConfig;
import com.example.app.core.ParserCoverage;
import com.example.app.core.pep.Country;
import com.example.app.core.pep.PepFunction;
import com.example.app.core.repository.CountryRepository;
import com.example.app.core.repository.DataSourceConfigRepository;
import com.example.app.core.repository.ParserCoverageRepository;
import com.example.app.core.repository.PepFunctionRepository;
import com.example.app.execution.ExecutionStatus;
import com.example.app.execution.SourceExecutionRepository;
import com.example.app.ingestion.converter.ConverterOptions;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * Création, modification, suppression et réinitialisation des sources depuis l'interface.
 *
 * <p>Une source enregistrée depuis l'interface est marquée « personnalisée » : le catalogue ne l'écrase
 * plus au démarrage. La suppression est logique : l'historique des exécutions est gardé et le catalogue ne
 * recrée pas la source ; recréer une source du même nom la fait revenir.
 */
@Service
public class SourceService {

    private final DataSourceConfigRepository sourceRepository;
    private final ParserCoverageRepository coverageRepository;
    private final PepFunctionRepository functionRepository;
    private final CountryRepository countryRepository;
    private final SourceExecutionRepository executionRepository;
    private final SourceCatalogLoader catalogLoader;
    private final String dataDir;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SourceService(DataSourceConfigRepository sourceRepository, ParserCoverageRepository coverageRepository,
                         PepFunctionRepository functionRepository, CountryRepository countryRepository,
                         SourceExecutionRepository executionRepository, SourceCatalogLoader catalogLoader,
                         @Value("${app.data-dir:data}") String dataDir) {
        this.sourceRepository = sourceRepository;
        this.coverageRepository = coverageRepository;
        this.functionRepository = functionRepository;
        this.countryRepository = countryRepository;
        this.executionRepository = executionRepository;
        this.catalogLoader = catalogLoader;
        this.dataDir = dataDir;
    }

    /** Source active (non supprimée), ou 404. */
    public DataSourceConfig find(Long id) {
        return sourceRepository.findById(id)
                .filter(s -> s.getDeletedAt() == null)
                .orElseThrow(() -> NotFoundException.of("Source", id));
    }

    @Transactional
    public DataSourceConfig create(SourceRequest request) {
        DataSourceConfig source = sourceRepository.findBySourceName(request.sourceName()).orElse(null);
        if (source != null && source.getDeletedAt() == null) {
            throw new ConflictException("Une source nommée « " + request.sourceName() + " » existe déjà.");
        }
        if (source == null) {
            source = new DataSourceConfig();
            source.setSourceName(request.sourceName());
        }
        source.setDeletedAt(null);       // une source supprimée du même nom revient
        return save(source, request, true);
    }

    @Transactional
    public DataSourceConfig update(Long id, SourceRequest request) {
        DataSourceConfig source = find(id);
        if (!source.getSourceName().equals(request.sourceName())) {
            throw new IllegalArgumentException("Le nom technique d'une source ne change pas après sa création "
                    + "(il préfixe les identifiants FTM déjà publiés).");
        }
        return save(source, request, false);
    }

    @Transactional
    public void delete(Long id) {
        DataSourceConfig source = find(id);
        if (executionRepository.existsByDataSourceConfigIdAndStatus(id, ExecutionStatus.RUNNING)) {
            throw new ConflictException("Une exécution de « " + name(source)
                    + " » est en cours : attendez sa fin pour supprimer la source.");
        }
        coverageRepository.deleteAll(coverageRepository.findByDataSourceConfigId(id));
        source.setDeletedAt(LocalDateTime.now());
        source.setEnabled(false);
        sourceRepository.save(source);
    }

    @Transactional
    public DataSourceConfig reset(Long id) {
        DataSourceConfig source = find(id);
        if (executionRepository.existsByDataSourceConfigIdAndStatus(id, ExecutionStatus.RUNNING)) {
            throw new ConflictException("Une exécution de « " + name(source) + " » est en cours.");
        }
        return catalogLoader.reset(source);
    }

    private DataSourceConfig save(DataSourceConfig source, SourceRequest r, boolean created) {
        String options = normalizeOptions(r.converterOptions());
        checkMapping(r.mappingFilePath());
        Country country = null;
        if (r.countryIso() != null && !r.countryIso().isBlank()) {
            String iso = r.countryIso().trim().toUpperCase();
            country = countryRepository.findById(iso).orElseThrow(() -> NotFoundException.of("Pays", iso));
        }

        source.setLabel(r.label().trim());
        source.setDescription(blankToNull(r.description()));
        source.setAttribution(blankToNull(r.attribution()));
        source.setSourceFormat(r.sourceFormat());
        source.setListType(r.listType());
        source.setParserType(r.parserType());
        source.setCountry(country);
        source.setRetentionMonths(r.retentionMonths());
        source.setExternalSourcePath(String.join("\n", r.externalSourcePath().trim().split("\\s+")));
        source.setMappingFilePath(blankToNull(r.mappingFilePath()));
        source.setConverterOptions(options);
        if (r.enabled() != null) {
            source.setEnabled(r.enabled());
        } else if (created) {
            source.setEnabled(true);
        }
        String base = dataDir.replace('\\', '/').replaceAll("/+$", "");
        String extension = r.sourceFormat().name().toLowerCase();
        source.setLocalSourcePath(base + "/sources/" + source.getSourceName() + "/source." + extension);
        source.setInitialJsonSourcePath(base + "/sources/" + source.getSourceName() + "/records.json");
        source.setFtmOutputPath(base + "/ftm/" + source.getSourceName() + ".ftm.json");
        source.setCustomized(true);
        DataSourceConfig saved = sourceRepository.save(source);

        coverageRepository.deleteAll(coverageRepository.findByDataSourceConfigId(saved.getId()));
        coverageRepository.flush();
        Set<Long> seen = new HashSet<>();
        if (r.coverage() != null) {
            for (SourceRequest.CoverageItem item : r.coverage()) {
                if (!seen.add(item.functionId())) {
                    continue;
                }
                PepFunction function = functionRepository.findById(item.functionId())
                        .orElseThrow(() -> NotFoundException.of("Fonction PEP", item.functionId()));
                coverageRepository.save(ParserCoverage.builder()
                        .dataSourceConfig(saved)
                        .pepFunction(function)
                        .completeness(item.completeness())
                        .includesFormer(item.includesFormer())
                        .build());
            }
        }
        return saved;
    }

    /** Options de conversion : JSON objet lisible par {@link ConverterOptions}, rendu compact ; vide → aucune. */
    private String normalizeOptions(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            if (node == null || !node.isObject()) {
                throw new IllegalArgumentException("Options de conversion : un objet JSON est attendu, ex. "
                        + "{\"encoding\": \"windows-1252\"}.");
            }
            objectMapper.treeToValue(node, ConverterOptions.class);
            return node.toString();
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalArgumentException("Options de conversion : JSON invalide (" + e.getOriginalMessage() + ").");
        }
    }

    /** Le fichier de mapping doit exister (ressource {@code classpath:} ou fichier du serveur). */
    private void checkMapping(String path) {
        if (path == null || path.isBlank()) {
            return;
        }
        String p = path.trim();
        boolean exists = p.startsWith("classpath:")
                ? getClass().getResource("/" + p.substring("classpath:".length()).replaceFirst("^/", "")) != null
                : Files.isRegularFile(Path.of(p));
        if (!exists) {
            throw new IllegalArgumentException("Fichier de mapping introuvable : " + p);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String name(DataSourceConfig s) {
        return s.getLabel() != null ? s.getLabel() : s.getSourceName();
    }
}
