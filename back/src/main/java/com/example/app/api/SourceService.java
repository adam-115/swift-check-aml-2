package com.example.app.api;

import com.example.app.api.dto.SourceRequest;
import com.example.app.core.Completeness;
import com.example.app.core.entities.Country;
import com.example.app.core.entities.DataSourceConfig;
import com.example.app.core.entities.ParserCoverage;
import com.example.app.core.entities.PepFunction;
import com.example.app.core.repository.CountryRepository;
import com.example.app.core.repository.DataSourceConfigRepository;
import com.example.app.core.repository.ParserCoverageRepository;
import com.example.app.core.repository.PepFunctionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;

/**
 * Création, modification et suppression des sources depuis l'interface, avec leur couverture PEP.
 * Les chemins de travail laissés vides sont calculés à partir du nom technique de la source.
 */
@Service
public class SourceService {

    private final DataSourceConfigRepository sourceRepository;
    private final ParserCoverageRepository coverageRepository;
    private final PepFunctionRepository functionRepository;
    private final CountryRepository countryRepository;
    private final ExecutionService executionService;
    private final String dataDir;

    public SourceService(DataSourceConfigRepository sourceRepository, ParserCoverageRepository coverageRepository,
                         PepFunctionRepository functionRepository, CountryRepository countryRepository,
                         ExecutionService executionService, @Value("${app.data-dir:data}") String dataDir) {
        this.sourceRepository = sourceRepository;
        this.coverageRepository = coverageRepository;
        this.functionRepository = functionRepository;
        this.countryRepository = countryRepository;
        this.executionService = executionService;
        this.dataDir = dataDir;
    }

    public DataSourceConfig find(Long id) {
        return sourceRepository.findById(id).orElseThrow(() -> NotFoundException.of("Source", id));
    }

    @Transactional
    public DataSourceConfig create(SourceRequest request) {
        if (sourceRepository.findBySourceName(request.sourceName()).isPresent()) {
            throw new ConflictException("Une source nommée « " + request.sourceName() + " » existe déjà.");
        }
        DataSourceConfig source = new DataSourceConfig();
        source.setSourceName(request.sourceName());
        return save(source, request, true);
    }

    @Transactional
    public DataSourceConfig update(Long id, SourceRequest request) {
        DataSourceConfig source = find(id);
        if (!source.getSourceName().equals(request.sourceName())) {
            sourceRepository.findBySourceName(request.sourceName()).ifPresent(other -> {
                throw new ConflictException("Une source nommée « " + request.sourceName() + " » existe déjà.");
            });
            source.setSourceName(request.sourceName());
        }
        return save(source, request, false);
    }

    @Transactional
    public DataSourceConfig setEnabled(Long id, boolean enabled) {
        DataSourceConfig source = find(id);
        source.setEnabled(enabled);
        return sourceRepository.save(source);
    }

    @Transactional
    public void delete(Long id) {
        DataSourceConfig source = find(id);
        executionService.deleteForSource(id);
        coverageRepository.deleteAll(coverageRepository.findByDataSourceConfigId(id));
        coverageRepository.flush();
        sourceRepository.delete(source);
        sourceRepository.flush();
    }

    private DataSourceConfig save(DataSourceConfig source, SourceRequest r, boolean created) {
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
        PepFunction mainFunction = r.pepFunctionId() == null ? null : functionRepository.findById(r.pepFunctionId())
                .orElseThrow(() -> NotFoundException.of("Fonction PEP", r.pepFunctionId()));
        source.setPepFunction(mainFunction);
        source.setRetentionMonths(r.retentionMonths());
        if (r.enabled() != null) {
            source.setEnabled(r.enabled());
        } else if (created) {
            source.setEnabled(true);
        }
        source.setExternalSourcePath(String.join("\n", r.externalSourcePath().trim().split("\\s+")));

        String base = dataDir.replace('\\', '/').replaceAll("/+$", "") + "/sources/" + source.getSourceName();
        String extension = r.sourceFormat().name().toLowerCase();
        source.setLocalSourcePath(orDefault(r.localSourcePath(), base + "/source." + extension));
        source.setInitialJsonSourcePath(orDefault(r.initialJsonSourcePath(), base + "/records.json"));
        source.setMappingFilePath(blankToNull(r.mappingFilePath()));
        source.setFtmOutputPath(orDefault(r.ftmOutputPath(), base + "/" + source.getSourceName() + ".ftm.json"));
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
                        .includesFormer(Boolean.TRUE.equals(item.includesFormer()))
                        .build());
            }
        }
        // La fonction principale fait toujours partie de la couverture.
        if (mainFunction != null && !seen.contains(mainFunction.getId())) {
            coverageRepository.save(ParserCoverage.builder()
                    .dataSourceConfig(saved)
                    .pepFunction(mainFunction)
                    .completeness(Completeness.PARTIAL)
                    .includesFormer(false)
                    .build());
        }
        return saved;
    }

    private static String orDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
