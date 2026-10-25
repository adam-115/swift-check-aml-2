package com.example.app.api;

import com.example.app.api.dto.ExecutionDto;
import com.example.app.execution.SourceExecution;
import com.example.app.execution.SourceExecutionRepository;
import com.example.app.ingestion.model.FtmEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Historique des exécutions.
 * <pre>
 * GET /api/executions?sourceId=&amp;limit=50  dernières exécutions
 * GET /api/executions/{id}                  détail, avec la trace d'erreur
 * GET /api/executions/{id}/entities?limit=  aperçu des entités du fichier FTM produit
 * </pre>
 */
@RestController
@RequestMapping("/api/executions")
public class ExecutionController {

    private static final int MAX_LIMIT = 1000;

    private final SourceExecutionRepository executionRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ExecutionController(SourceExecutionRepository executionRepository) {
        this.executionRepository = executionRepository;
    }

    @GetMapping
    public List<ExecutionDto> list(@RequestParam(required = false) Long sourceId,
                                   @RequestParam(defaultValue = "50") int limit) {
        PageRequest page = PageRequest.of(0, Math.clamp(limit, 1, MAX_LIMIT));
        List<SourceExecution> executions = sourceId == null
                ? executionRepository.findAllByOrderByStartedAtDesc(page)
                : executionRepository.findByDataSourceConfigIdOrderByStartedAtDesc(sourceId, page);
        return executions.stream().map(ExecutionDto::summary).toList();
    }

    @GetMapping("/{id}")
    public ExecutionDto get(@PathVariable Long id) {
        return ExecutionDto.detail(find(id));
    }

    @GetMapping("/{id}/entities")
    public List<FtmEntity> entities(@PathVariable Long id, @RequestParam(defaultValue = "200") int limit)
            throws IOException {
        SourceExecution execution = find(id);
        if (execution.getOutputPath() == null) {
            throw new NotFoundException("Cette exécution n'a pas produit de fichier FTM.");
        }
        Path path = Path.of(execution.getOutputPath());
        if (!Files.isRegularFile(path)) {
            throw new NotFoundException("Fichier FTM introuvable (remplacé ou supprimé) : " + path);
        }
        int max = Math.clamp(limit, 1, MAX_LIMIT);
        List<FtmEntity> entities = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            while (entities.size() < max && (line = reader.readLine()) != null) {
                if (!line.isBlank()) {
                    entities.add(objectMapper.readValue(line, FtmEntity.class));
                }
            }
        }
        return entities;
    }

    private SourceExecution find(Long id) {
        return executionRepository.findById(id).orElseThrow(() -> NotFoundException.of("Exécution", id));
    }
}
