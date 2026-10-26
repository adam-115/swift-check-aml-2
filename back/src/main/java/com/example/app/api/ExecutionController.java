package com.example.app.api;

import com.example.app.api.ExecutionService.FileKind;
import com.example.app.api.dto.EntityPage;
import com.example.app.api.dto.ExecutionDto;
import com.example.app.core.ExecutionStatus;
import com.example.app.core.entities.SourceExecution;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * Exécutions des sources.
 * <pre>
 * POST /api/sources/{id}/executions        lance FtmGeneratorService en arrière-plan (202)
 * GET  /api/sources/{id}/executions        historique, la plus récente d'abord
 * GET  /api/executions?sourceId&amp;status     dernières exécutions, toutes sources
 * GET  /api/executions/{id}                une exécution, avec la pile d'appels en cas d'échec
 * GET  /api/executions/{id}/entities       entités FTM produites (?schema&amp;q&amp;page&amp;size)
 * GET  /api/executions/{id}/files/{kind}   télécharge source, json, mapping ou ftm
 * </pre>
 */
@RestController
public class ExecutionController {

    private final ExecutionService executionService;
    private final ExecutionEntityService entityService;

    public ExecutionController(ExecutionService executionService, ExecutionEntityService entityService) {
        this.executionService = executionService;
        this.entityService = entityService;
    }

    @GetMapping("/api/executions")
    public List<ExecutionDto> recent(@RequestParam(required = false) Long sourceId,
                                     @RequestParam(required = false) ExecutionStatus status) {
        return executionService.recent(sourceId, status).stream().map(ExecutionDto::of).toList();
    }

    @GetMapping("/api/executions/{id}/entities")
    public EntityPage entities(@PathVariable Long id,
                               @RequestParam(required = false) String schema,
                               @RequestParam(required = false) String q,
                               @RequestParam(defaultValue = "0") int page,
                               @RequestParam(defaultValue = "50") int size) {
        return entityService.page(executionService.find(id), schema, q, page, size);
    }

    @PostMapping("/api/sources/{id}/executions")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ExecutionDto start(@PathVariable Long id) {
        return ExecutionDto.of(executionService.start(id));
    }

    @GetMapping("/api/sources/{id}/executions")
    public List<ExecutionDto> history(@PathVariable Long id) {
        return executionService.history(id).stream().map(ExecutionDto::of).toList();
    }

    @GetMapping("/api/executions/{id}")
    public ExecutionDto get(@PathVariable Long id) {
        return ExecutionDto.detail(executionService.find(id));
    }

    @GetMapping("/api/executions/{id}/files/{kind}")
    public ResponseEntity<Resource> download(@PathVariable Long id, @PathVariable String kind) {
        FileKind fileKind;
        try {
            fileKind = FileKind.valueOf(kind.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Fichier inconnu : " + kind + " (source, json, mapping ou ftm).");
        }
        SourceExecution execution = executionService.find(id);
        Path file = executionService.file(execution, fileKind)
                .orElseThrow(() -> new NotFoundException(fileKind.label + " absent pour l'exécution " + id + "."));
        String name = execution.getDataSourceConfig().getSourceName() + "_" + id + "_" + file.getFileName();
        MediaType type = name.endsWith(".json") ? MediaType.APPLICATION_JSON : MediaType.APPLICATION_OCTET_STREAM;
        return ResponseEntity.ok()
                .contentType(type)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(name, StandardCharsets.UTF_8).build().toString())
                .body(new FileSystemResource(file));
    }
}
