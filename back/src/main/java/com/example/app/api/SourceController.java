package com.example.app.api;

import com.example.app.api.dto.SourceDto;
import com.example.app.api.dto.SourceRequest;
import com.example.app.core.entities.DataSourceConfig;
import com.example.app.core.ListType;
import com.example.app.core.repository.DataSourceConfigRepository;
import com.example.app.core.repository.ParserCoverageRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
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

/**
 * Sources de données.
 * <pre>
 * GET    /api/sources?listType=PEP&amp;country=LU  sources et leur couverture PEP
 * GET    /api/sources/{id}                       une source
 * POST   /api/sources                            crée une source (201)
 * PUT    /api/sources/{id}                       modifie une source
 * PATCH  /api/sources/{id}/enabled               active ou désactive
 * DELETE /api/sources/{id}                       supprime une source (204)
 * </pre>
 */
@RestController
@RequestMapping("/api/sources")
public class SourceController {

    /** Corps de PATCH /enabled. */
    public record EnabledRequest(@NotNull(message = "obligatoire") Boolean enabled) {
    }

    private final DataSourceConfigRepository sourceRepository;
    private final ParserCoverageRepository coverageRepository;
    private final SourceService sourceService;
    private final ExecutionService executionService;

    public SourceController(DataSourceConfigRepository sourceRepository, ParserCoverageRepository coverageRepository,
                            SourceService sourceService, ExecutionService executionService) {
        this.sourceRepository = sourceRepository;
        this.coverageRepository = coverageRepository;
        this.sourceService = sourceService;
        this.executionService = executionService;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<SourceDto> list(@RequestParam(required = false) ListType listType,
                                @RequestParam(required = false) String country) {
        return sourceRepository.findAll().stream()
                .filter(s -> listType == null || listType == s.getListType())
                .filter(s -> country == null || country.isBlank()
                        || (s.getCountry() != null && s.getCountry().getIso().equalsIgnoreCase(country)))
                .sorted(Comparator.comparing((DataSourceConfig s) -> s.getLabel() != null ? s.getLabel() : "",
                                String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(s -> s.getSourceName() != null ? s.getSourceName() : ""))
                .map(this::toDto)
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
    public SourceDto update(@PathVariable Long id, @Valid @RequestBody SourceRequest request) {
        return toDto(sourceService.update(id, request));
    }

    @PatchMapping("/{id}/enabled")
    public SourceDto setEnabled(@PathVariable Long id, @Valid @RequestBody EnabledRequest request) {
        return toDto(sourceService.setEnabled(id, request.enabled()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        sourceService.delete(id);
    }

    private SourceDto toDto(DataSourceConfig s) {
        return SourceDto.of(s, coverageRepository.findByDataSourceConfigId(s.getId()),
                executionService.last(s.getId()).orElse(null));
    }
}
