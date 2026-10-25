package com.example.app.api;

import com.example.app.api.dto.ExecutionDto;
import com.example.app.api.dto.SourceDto;
import com.example.app.api.dto.SourceRequest;
import com.example.app.api.dto.SourceUpdateRequest;
import com.example.app.catalog.SourceCatalogLoader;
import com.example.app.core.DataSourceConfig;
import com.example.app.core.ListType;
import com.example.app.core.repository.DataSourceConfigRepository;
import com.example.app.core.repository.ParserCoverageRepository;
import com.example.app.execution.ExecutionService;
import com.example.app.execution.SourceExecutionRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Sources de données : consultation, gestion et exécution.
 * <pre>
 * GET    /api/sources?listType=PEP&amp;country=LU  sources, avec couverture et dernière exécution
 * GET    /api/sources/{id}                       une source
 * POST   /api/sources                            crée une source (201)
 * PUT    /api/sources/{id}                       modifie toute la source
 * PATCH  /api/sources/{id}                       active / désactive, change l'adresse
 * DELETE /api/sources/{id}                       supprime la source (204, historique gardé)
 * POST   /api/sources/{id}/reset                 remet la source dans l'état du catalogue
 * POST   /api/sources/{id}/executions            lance une exécution (202, en arrière-plan)
 * </pre>
 */
@RestController
@RequestMapping("/api/sources")
public class SourceController {

    private final DataSourceConfigRepository sourceRepository;
    private final ParserCoverageRepository coverageRepository;
    private final SourceExecutionRepository executionRepository;
    private final ExecutionService executionService;
    private final SourceService sourceService;
    private final SourceCatalogLoader catalogLoader;

    public SourceController(DataSourceConfigRepository sourceRepository, ParserCoverageRepository coverageRepository,
                            SourceExecutionRepository executionRepository, ExecutionService executionService,
                            SourceService sourceService, SourceCatalogLoader catalogLoader) {
        this.sourceRepository = sourceRepository;
        this.coverageRepository = coverageRepository;
        this.executionRepository = executionRepository;
        this.executionService = executionService;
        this.sourceService = sourceService;
        this.catalogLoader = catalogLoader;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<SourceDto> list(@RequestParam(required = false) ListType listType,
                                @RequestParam(required = false) String country) {
        Set<String> catalog = catalogLoader.catalogNames();
        return sourceRepository.findAll().stream()
                .filter(s -> s.getDeletedAt() == null)
                .filter(s -> listType == null || listType == s.getListType())
                .filter(s -> country == null || country.isBlank()
                        || (s.getCountry() != null && s.getCountry().getIso().equalsIgnoreCase(country)))
                .sorted(Comparator.comparing((DataSourceConfig s) -> s.getLabel() != null ? s.getLabel() : "")
                        .thenComparing(s -> s.getSourceName() != null ? s.getSourceName() : ""))
                .map(s -> toDto(s, catalog))
                .toList();
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public SourceDto get(@PathVariable Long id) {
        return toDto(sourceService.find(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SourceDto create(@Valid @RequestBody SourceRequest request) {
        return toDto(sourceService.create(request));
    }

    @PutMapping("/{id}")
    public SourceDto replace(@PathVariable Long id, @Valid @RequestBody SourceRequest request) {
        return toDto(sourceService.update(id, request));
    }

    @PatchMapping("/{id}")
    @Transactional
    public SourceDto update(@PathVariable Long id, @Valid @RequestBody SourceUpdateRequest request) {
        DataSourceConfig source = sourceService.find(id);
        if (request.enabled() != null) {
            source.setEnabled(request.enabled());
        }
        if (request.externalSourcePath() != null) {
            String path = request.externalSourcePath().trim();
            if (path.isEmpty()) {
                throw new IllegalArgumentException("L'adresse de la source ne peut pas être vide.");
            }
            source.setExternalSourcePath(path);
            source.setCustomized(true);
        }
        return toDto(sourceRepository.save(source));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        sourceService.delete(id);
    }

    @PostMapping("/{id}/reset")
    public SourceDto reset(@PathVariable Long id) {
        return toDto(sourceService.reset(id));
    }

    @PostMapping("/{id}/executions")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ExecutionDto run(@PathVariable Long id) {
        sourceService.find(id);
        return ExecutionDto.summary(executionService.start(id));
    }

    private SourceDto toDto(DataSourceConfig s) {
        return toDto(s, catalogLoader.catalogNames());
    }

    private SourceDto toDto(DataSourceConfig s, Set<String> catalog) {
        ExecutionDto last = executionRepository.findFirstByDataSourceConfigIdOrderByStartedAtDesc(s.getId())
                .map(ExecutionDto::summary).orElse(null);
        return SourceDto.of(s, coverageRepository.findByDataSourceConfigId(s.getId()), last,
                catalog.contains(s.getSourceName()));
    }
}
