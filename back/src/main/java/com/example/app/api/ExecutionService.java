package com.example.app.api;

import com.example.app.core.ExecutionStatus;
import com.example.app.core.entities.DataSourceConfig;
import com.example.app.core.entities.SourceExecution;
import com.example.app.core.repository.DataSourceConfigRepository;
import com.example.app.core.repository.SourceExecutionRepository;
import com.example.app.ingestion.model.FtmEntity;
import com.example.app.ingestion.service.FtmGeneratorService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Exécution d'une source : lance {@link FtmGeneratorService#generate} en arrière-plan selon la configuration de la
 * source, enregistre l'historique et archive les quatre fichiers de l'exécution dans
 * {@code <app.data-dir>/executions/<id>/} pour qu'ils restent téléchargeables après les exécutions suivantes.
 */
@Slf4j
@Service
public class ExecutionService {

    /** Fichiers d'une exécution, avec le nom sous lequel ils sont archivés. */
    public enum FileKind {
        SOURCE("source", "Fichier source"),
        JSON("records.json", "JSON intermédiaire"),
        MAPPING("mapping.json", "Fichier de mapping"),
        FTM("ftm.json", "Entités FTM");

        /** Nom archivé ; pour la source, préfixe complété par l'extension d'origine. */
        public final String archiveName;
        public final String label;

        FileKind(String archiveName, String label) {
            this.archiveName = archiveName;
            this.label = label;
        }
    }

    private static final int MAX_ERROR_LENGTH = 8000;

    private final DataSourceConfigRepository sourceRepository;
    private final SourceExecutionRepository executionRepository;
    private final FtmGeneratorService generator;
    private final TaskExecutor taskExecutor;
    private final Path executionsDir;

    public ExecutionService(DataSourceConfigRepository sourceRepository, SourceExecutionRepository executionRepository,
                            FtmGeneratorService generator,
                            @Qualifier("applicationTaskExecutor") TaskExecutor taskExecutor,
                            @Value("${app.data-dir:data}") String dataDir) {
        this.sourceRepository = sourceRepository;
        this.executionRepository = executionRepository;
        this.generator = generator;
        this.taskExecutor = taskExecutor;
        this.executionsDir = Paths.get(dataDir, "executions");
    }

    /** Une exécution restée « en cours » a été interrompue par l'arrêt de l'application. */
    @EventListener(ApplicationReadyEvent.class)
    public void failInterruptedExecutions() {
        for (SourceExecution execution : executionRepository.findByStatus(ExecutionStatus.RUNNING)) {
            execution.setStatus(ExecutionStatus.FAILED);
            execution.setFinishedAt(LocalDateTime.now());
            execution.setErrorMessage("Interrompue : l'application s'est arrêtée pendant l'exécution.");
            executionRepository.save(execution);
        }
    }

    /**
     * Enregistre une exécution « en cours » et lance la génération en arrière-plan.
     *
     * @throws ConflictException si la source est désactivée ou déjà en cours d'exécution
     */
    public SourceExecution start(Long sourceId) {
        DataSourceConfig source = sourceRepository.findById(sourceId)
                .orElseThrow(() -> NotFoundException.of("Source", sourceId));
        if (!source.isEnabled()) {
            throw new ConflictException("La source « " + source.getSourceName() + " » est désactivée.");
        }
        if (executionRepository.existsByDataSourceConfigIdAndStatus(sourceId, ExecutionStatus.RUNNING)) {
            throw new ConflictException("La source « " + source.getSourceName() + " » est déjà en cours d'exécution.");
        }
        SourceExecution execution = executionRepository.save(SourceExecution.builder()
                .dataSourceConfig(source)
                .status(ExecutionStatus.RUNNING)
                .startedAt(LocalDateTime.now())
                .build());
        taskExecutor.execute(() -> run(execution.getId()));
        return execution;
    }

    public SourceExecution find(Long id) {
        return executionRepository.findById(id).orElseThrow(() -> NotFoundException.of("Exécution", id));
    }

    public List<SourceExecution> history(Long sourceId) {
        if (!sourceRepository.existsById(sourceId)) {
            throw NotFoundException.of("Source", sourceId);
        }
        return executionRepository.findByDataSourceConfigIdOrderByIdDesc(sourceId);
    }

    /** Dernières exécutions, filtrées par source et par état (filtres facultatifs). */
    public List<SourceExecution> recent(Long sourceId, ExecutionStatus status) {
        List<SourceExecution> executions = sourceId != null
                ? history(sourceId)
                : executionRepository.findTop500ByOrderByIdDesc();
        return executions.stream().filter(e -> status == null || e.getStatus() == status).toList();
    }

    public Optional<SourceExecution> last(Long sourceId) {
        return executionRepository.findFirstByDataSourceConfigIdOrderByIdDesc(sourceId);
    }

    /** Fichier archivé d'une exécution, s'il existe. */
    public Optional<Path> file(SourceExecution execution, FileKind kind) {
        if (execution.getArchiveDir() == null) {
            return Optional.empty();
        }
        Path dir = Paths.get(execution.getArchiveDir());
        if (!Files.isDirectory(dir)) {
            return Optional.empty();
        }
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(p -> kind == FileKind.SOURCE
                            ? p.getFileName().toString().startsWith(kind.archiveName)
                            : p.getFileName().toString().equals(kind.archiveName))
                    .findFirst();
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    /** Supprime l'historique et les archives d'une source (avant la suppression de la source). */
    public void deleteForSource(Long sourceId) {
        List<SourceExecution> executions = executionRepository.findByDataSourceConfigIdOrderByIdDesc(sourceId);
        for (SourceExecution execution : executions) {
            deleteArchive(execution);
        }
        executionRepository.deleteAll(executions);
        executionRepository.flush();
    }

    private void run(Long executionId) {
        SourceExecution execution = find(executionId);
        DataSourceConfig source = execution.getDataSourceConfig();
        log.info("Exécution {} de la source {}", executionId, source.getSourceName());
        // Durée mesurée ici : les dates en base sont arrondies à la seconde
        long started = System.nanoTime();
        try {
            List<FtmEntity> entities = generator.generate(source);
            Map<String, Long> bySchema = entities.stream()
                    .collect(Collectors.groupingBy(FtmEntity::getSchema, TreeMap::new, Collectors.counting()));
            execution.setEntityCount(entities.size());
            execution.setSchemaCounts(bySchema.entrySet().stream()
                    .map(e -> e.getKey() + ": " + e.getValue())
                    .collect(Collectors.joining(", ")));
            execution.setStatus(ExecutionStatus.SUCCESS);
        } catch (Throwable e) {
            log.warn("Échec de l'exécution {} de la source {}", executionId, source.getSourceName(), e);
            execution.setStatus(ExecutionStatus.FAILED);
            execution.setErrorMessage(describe(e));
            execution.setErrorCause(summary(rootCause(e)));
            execution.setErrorStack(stackTrace(e));
            execution.setFailedStep(failedStep(e, source));
        }
        // Les fichiers sont archivés même en cas d'échec : la source téléchargée aide à comprendre l'erreur.
        archive(execution, source);
        if (execution.getStatus() == ExecutionStatus.FAILED && execution.getFailedStep() == null) {
            // Étape déduite des fichiers produits par cette exécution
            execution.setFailedStep(execution.getSourceSize() == null ? "DOWNLOAD"
                    : execution.getJsonSize() == null ? "CONVERSION"
                    : execution.getFtmSize() == null ? "MAPPING" : "EXPORT");
        }
        execution.setFinishedAt(LocalDateTime.now());
        execution.setDurationMs(Duration.ofNanos(System.nanoTime() - started).toMillis());
        executionRepository.save(execution);
    }

    /**
     * Archive les fichiers de l'exécution. Le mapping (et la source quand elle n'est pas téléchargée) sont des
     * entrées, toujours archivées ; un fichier produit (source téléchargée, JSON, FTM) n'est archivé que s'il a été
     * écrit pendant cette exécution, pour ne pas présenter le fichier d'une exécution précédente.
     */
    private void archive(SourceExecution execution, DataSourceConfig source) {
        Path dir = executionsDir.resolve(String.valueOf(execution.getId()));
        Instant since = execution.getStartedAt().atZone(ZoneId.systemDefault()).toInstant()
                .minusSeconds(2);
        boolean downloaded = source.getExternalSourcePath() != null && !source.getExternalSourcePath().isBlank();
        try {
            Files.createDirectories(dir);
            execution.setArchiveDir(dir.toString().replace('\\', '/'));
            execution.setSourceSize(copy(source.getLocalSourcePath(),
                    dir.resolve(FileKind.SOURCE.archiveName + extension(source.getLocalSourcePath())),
                    downloaded ? since : null));
            execution.setJsonSize(copy(source.getInitialJsonSourcePath(), dir.resolve(FileKind.JSON.archiveName), since));
            execution.setMappingSize(copy(source.getMappingFilePath(), dir.resolve(FileKind.MAPPING.archiveName), null));
            execution.setFtmSize(copy(source.getFtmOutputPath(), dir.resolve(FileKind.FTM.archiveName), since));
        } catch (IOException e) {
            log.warn("Archivage impossible pour l'exécution {}", execution.getId(), e);
        }
    }

    /**
     * Copie un fichier (chemin système ou {@code classpath:}) ; renvoie sa taille, ou null s'il n'existe pas ou
     * s'il n'a pas été modifié depuis {@code modifiedSince}.
     */
    private Long copy(String path, Path target, Instant modifiedSince) throws IOException {
        if (path == null || path.isBlank()) {
            return null;
        }
        String classpath = path.startsWith("classpath:") ? path.substring("classpath:".length()) : null;
        if (classpath == null) {
            Path file = Paths.get(path);
            if (!Files.isRegularFile(file)) {
                return null;
            }
            if (modifiedSince != null && Files.getLastModifiedTime(file).toInstant().isBefore(modifiedSince)) {
                return null;
            }
            Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING);
            return Files.size(target);
        }
        try (InputStream in = getClass().getResourceAsStream(classpath.startsWith("/") ? classpath : "/" + classpath)) {
            if (in == null) {
                return null;
            }
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            return Files.size(target);
        }
    }

    private void deleteArchive(SourceExecution execution) {
        if (execution.getArchiveDir() == null) {
            return;
        }
        Path dir = Paths.get(execution.getArchiveDir());
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(dir)) {
            for (Path p : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(p);
            }
        } catch (IOException e) {
            log.warn("Archive non supprimée : {}", dir, e);
        }
    }

    private static String extension(String path) {
        if (path == null) {
            return "";
        }
        String name = path.substring(path.replace('\\', '/').lastIndexOf('/') + 1);
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot);
    }

    /**
     * Étape en échec, d'après la pile d'appels : téléchargement ({@code downloadIfPresent}), conversion (un
     * convertisseur), mapping ({@code DynamicFtmMapper}, normaliseurs, fichier de mapping introuvable). Null si la
     * pile ne permet pas de conclure.
     */
    private static String failedStep(Throwable e, DataSourceConfig source) {
        for (Throwable t = e; t != null; t = t.getCause() == t ? null : t.getCause()) {
            String message = t.getMessage() == null ? "" : t.getMessage();
            if (source.getMappingFilePath() != null && message.contains(stripClasspath(source.getMappingFilePath()))) {
                return "MAPPING";
            }
            for (StackTraceElement frame : t.getStackTrace()) {
                String cls = frame.getClassName();
                if (cls.endsWith(".FtmGeneratorService") && frame.getMethodName().equals("downloadIfPresent")) {
                    return "DOWNLOAD";
                }
                if (cls.contains(".ingestion.converter.")) {
                    return "CONVERSION";
                }
                if (cls.endsWith(".DynamicFtmMapper") || cls.contains(".core.normalizer.")
                        || cls.endsWith(".MappingConfig") || cls.endsWith(".MappingRule")) {
                    return "MAPPING";
                }
            }
        }
        return null;
    }

    private static String stripClasspath(String path) {
        return path.startsWith("classpath:") ? path.substring("classpath:".length()) : path;
    }

    private static Throwable rootCause(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root;
    }

    /** « FileNotFoundException : Impossible de trouver le fichier : … (FtmGeneratorService.java:173) ». */
    private static String summary(Throwable t) {
        StringBuilder sb = new StringBuilder(t.getClass().getName());
        if (t.getMessage() != null) {
            sb.append(" : ").append(t.getMessage());
        }
        if (t.getStackTrace().length > 0) {
            StackTraceElement at = t.getStackTrace()[0];
            sb.append("\n    à ").append(at.getClassName()).append('.').append(at.getMethodName())
                    .append('(').append(at.getFileName()).append(':').append(at.getLineNumber()).append(')');
        }
        return sb.toString();
    }

    private static String stackTrace(Throwable e) {
        StringWriter out = new StringWriter();
        e.printStackTrace(new PrintWriter(out));
        String trace = out.toString();
        return trace.length() > 60_000 ? trace.substring(0, 60_000) + "\n…" : trace;
    }

    /** Message d'erreur avec ses causes, ex. « IllegalStateException: Échec du téléchargement… ← … ». */
    private static String describe(Throwable e) {
        StringBuilder sb = new StringBuilder();
        for (Throwable t = e; t != null && sb.length() < MAX_ERROR_LENGTH; t = t.getCause() == t ? null : t.getCause()) {
            if (!sb.isEmpty()) {
                sb.append("\n← ");
            }
            sb.append(t.getClass().getSimpleName());
            if (t.getMessage() != null) {
                sb.append(" : ").append(t.getMessage());
            }
        }
        return sb.length() > MAX_ERROR_LENGTH ? sb.substring(0, MAX_ERROR_LENGTH) : sb.toString();
    }
}
