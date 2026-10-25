package com.example.app.execution;

import com.example.app.api.ConflictException;
import com.example.app.api.NotFoundException;
import com.example.app.core.DataSourceConfig;
import com.example.app.core.repository.DataSourceConfigRepository;
import com.example.app.ingestion.IngestionException;
import com.example.app.ingestion.model.GenerationReport;
import com.example.app.ingestion.service.FtmGeneratorService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDateTime;

/**
 * Lance le pipeline FTM d'une source et enregistre chaque exécution ({@link SourceExecution}).
 *
 * <p>{@link #start(Long)} rend la main tout de suite avec une exécution « en cours » ; le pipeline tourne
 * en arrière-plan et l'interface suit son état. {@link #runNow(Long)} attend la fin (tests, scripts).
 */
@Slf4j
@Service
public class ExecutionService {

    static final String INTERRUPTED_MESSAGE = "Exécution interrompue par l'arrêt de l'application.";
    private static final int MAX_DETAIL_LENGTH = 20_000;

    private final DataSourceConfigRepository sourceRepository;
    private final SourceExecutionRepository executionRepository;
    private final FtmGeneratorService generatorService;
    private final TaskExecutor executor;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ExecutionService(DataSourceConfigRepository sourceRepository,
                            SourceExecutionRepository executionRepository,
                            FtmGeneratorService generatorService,
                            @Qualifier("sourceExecutor") TaskExecutor executor) {
        this.sourceRepository = sourceRepository;
        this.executionRepository = executionRepository;
        this.generatorService = generatorService;
        this.executor = executor;
    }

    /** Au démarrage, les exécutions restées « en cours » sont marquées en échec. */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void failInterruptedExecutions() {
        int count = executionRepository.failInterrupted(LocalDateTime.now(), INTERRUPTED_MESSAGE);
        if (count > 0) {
            log.warn("{} exécution(s) interrompue(s) marquée(s) en échec", count);
        }
    }

    /** Démarre l'exécution en arrière-plan et rend l'exécution « en cours ». */
    public SourceExecution start(Long sourceId) {
        SourceExecution execution = create(sourceId);
        executor.execute(() -> complete(execution));
        return execution;
    }

    /** Exécute la source et attend la fin. */
    public SourceExecution runNow(Long sourceId) {
        return complete(create(sourceId));
    }

    private synchronized SourceExecution create(Long sourceId) {
        DataSourceConfig source = sourceRepository.findById(sourceId)
                .filter(s -> s.getDeletedAt() == null)
                .orElseThrow(() -> NotFoundException.of("Source", sourceId));
        if (!source.isEnabled()) {
            throw new ConflictException("La source « " + displayName(source) + " » est désactivée.");
        }
        if (source.getMappingFilePath() == null || source.getMappingFilePath().isBlank()) {
            throw new ConflictException("La source « " + displayName(source)
                    + " » n'a pas encore de fichier de mapping : elle ne peut pas être exécutée.");
        }
        if (executionRepository.existsByDataSourceConfigIdAndStatus(sourceId, ExecutionStatus.RUNNING)) {
            throw new ConflictException("Une exécution de « " + displayName(source) + " » est déjà en cours.");
        }
        return executionRepository.save(SourceExecution.builder()
                .dataSourceConfig(source)
                .status(ExecutionStatus.RUNNING)
                .startedAt(LocalDateTime.now())
                .build());
    }

    private SourceExecution complete(SourceExecution execution) {
        DataSourceConfig source = execution.getDataSourceConfig();
        try {
            GenerationReport report = generatorService.run(source);
            execution.setStatus(ExecutionStatus.SUCCESS);
            execution.setSourceFiles(report.sourceFiles());
            execution.setSourceRecords(report.sourceRecords());
            execution.setMappedRecords(report.mappedRecords());
            execution.setEntityCount(report.entities().size());
            execution.setPersonCount((int) report.count("Person"));
            execution.setEntitiesBySchema(objectMapper.writeValueAsString(report.entitiesBySchema()));
            execution.setOutputPath(source.getFtmOutputPath());
            log.info("Exécution {} de {} terminée : {} entités", execution.getId(), source.getSourceName(),
                    report.entities().size());
        } catch (IngestionException e) {
            fail(execution, e.getStep(), e.getMessage(), e);
        } catch (Exception e) {
            fail(execution, null, "Erreur inattendue : " + e.getMessage(), e);
        }
        execution.setFinishedAt(LocalDateTime.now());
        return executionRepository.save(execution);
    }

    private void fail(SourceExecution execution, IngestionException.Step step, String message, Exception e) {
        log.warn("Exécution {} de {} en échec ({}) : {}", execution.getId(),
                execution.getDataSourceConfig().getSourceName(), step, message);
        execution.setStatus(ExecutionStatus.FAILED);
        execution.setFailedStep(step);
        execution.setErrorMessage(message != null && message.length() > 2000 ? message.substring(0, 2000) : message);
        StringWriter trace = new StringWriter();
        e.printStackTrace(new PrintWriter(trace));
        String detail = trace.toString();
        execution.setErrorDetail(detail.length() > MAX_DETAIL_LENGTH ? detail.substring(0, MAX_DETAIL_LENGTH) : detail);
    }

    private static String displayName(DataSourceConfig source) {
        return source.getLabel() != null ? source.getLabel() : source.getSourceName();
    }
}
