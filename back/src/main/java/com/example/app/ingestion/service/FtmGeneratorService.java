package com.example.app.ingestion.service;

import com.example.app.core.DataSourceConfig;
import com.example.app.core.MappingConfig;
import com.example.app.core.MappingRule;
import com.example.app.ingestion.IngestionException;
import com.example.app.ingestion.IngestionException.Step;
import com.example.app.ingestion.converter.ConverterOptions;
import com.example.app.ingestion.converter.CsvSourceConverter;
import com.example.app.ingestion.converter.HtmlSourceConverter;
import com.example.app.ingestion.converter.IDynamicSourceConverter;
import com.example.app.ingestion.converter.JsonSourceConverter;
import com.example.app.ingestion.converter.PdfSourceConverter;
import com.example.app.ingestion.converter.XlsxSourceConverter;
import com.example.app.ingestion.converter.XmlSourceConverter;
import com.example.app.ingestion.engine.DocumentFetcher;
import com.example.app.ingestion.engine.DynamicFtmMapper;
import com.example.app.ingestion.engine.RecordPreprocessor;
import com.example.app.ingestion.model.FtmEntity;
import com.example.app.ingestion.model.GenerationReport;
import com.example.app.ingestion.model.SourceFormat;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Service d'orchestration pour la génération FollowTheMoney (FTM) à partir
 * d'une configuration DataSourceConfig.
 * Étapes :
 * 1. Téléchargement depuis externalSourcePath vers localSourcePath (si URL
 * fournie). Plusieurs adresses, une par ligne, donnent plusieurs fichiers.
 * 2. Conversion dynamique selon sourceFormat vers initialJsonSourcePath (JSON),
 * avec les options de converterOptions (encodage, sélecteurs HTML).
 * 3. Mapping FTM via DynamicFtmMapper avec mappingFilePath, après calcul des
 * champs dérivés et filtrage des enregistrements.
 * 4. Fusion des entités de même identifiant et export dans ftmOutputPath au
 * format NDJSON.
 *
 * <p>Chaque enregistrement converti reçoit {@code _dataset} (nom de la source) et
 * {@code _sourceUrl} (adresse du fichier lu), utilisables dans le mapping.
 * Une erreur est levée en {@link IngestionException} avec l'étape qui a échoué.
 */
@Slf4j
@Service
public class FtmGeneratorService {

    private static final int DOWNLOAD_ATTEMPTS = 3;

    private final Map<SourceFormat, IDynamicSourceConverter> converters = new EnumMap<>(SourceFormat.class);
    private final DynamicFtmMapper ftmMapper;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public FtmGeneratorService() {
        this(List.of(
                new XmlSourceConverter(),
                new JsonSourceConverter(),
                new CsvSourceConverter(),
                new XlsxSourceConverter(),
                new HtmlSourceConverter(),
                new PdfSourceConverter()), new DynamicFtmMapper());
    }

    public FtmGeneratorService(List<IDynamicSourceConverter> converterList, DynamicFtmMapper ftmMapper) {
        this.ftmMapper = ftmMapper;
        this.objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        for (IDynamicSourceConverter converter : converterList) {
            converters.put(converter.getSupportedFormat(), converter);
        }
    }

    /**
     * Exécute le pipeline complet de génération FTM à partir de la configuration
     * DataSourceConfig.
     *
     * @param config configuration de la source de données
     * @return liste des entités FtmEntity générées
     * @throws IngestionException en cas d'erreur lors du téléchargement, conversion, mapping
     *                            ou écriture
     */
    public List<FtmEntity> generate(DataSourceConfig config) {
        return run(config).entities();
    }

    /**
     * Comme {@link #generate(DataSourceConfig)}, avec les chiffres de chaque étape.
     */
    public GenerationReport run(DataSourceConfig config) {
        log.info("Démarrage de la génération FTM : source={}, format={}, mapping={}",
                config.getSourceName(), config.getSourceFormat(), config.getMappingFilePath());

        IDynamicSourceConverter converter = converters.get(config.getSourceFormat());
        if (converter == null) {
            throw new IngestionException(Step.CONFIGURATION,
                    "Aucun convertisseur disponible pour le format : " + config.getSourceFormat());
        }
        ConverterOptions options = readConverterOptions(config);
        MappingConfig mappingConfig = readMappingConfig(config.getMappingFilePath());

        // 1. Téléchargement depuis externalSourcePath si spécifié
        List<SourceFile> files = download(config);

        // 2. Conversion de chaque fichier source selon sourceFormat
        List<Map<String, Object>> records = new ArrayList<>();
        for (SourceFile file : files) {
            List<Map<String, Object>> converted;
            try (InputStream is = resolveInputStream(file.localPath())) {
                converted = converter.convertToDynamicJson(is, options.withBaseUrl(file.origin()));
            } catch (Exception e) {
                throw new IngestionException(Step.CONVERSION, "Impossible de convertir le fichier "
                        + file.origin() + " (" + config.getSourceFormat() + ") : " + rootMessage(e), e);
            }
            for (Map<String, Object> record : converted) {
                Map<String, Object> enriched = new LinkedHashMap<>(record);
                enriched.put("_dataset", config.getSourceName());
                enriched.put("_sourceUrl", file.origin());
                records.add(enriched);
            }
        }
        if (records.isEmpty()) {
            throw new IngestionException(Step.CONVERSION, "Aucun enregistrement trouvé dans la source : "
                    + "le fichier est vide ou la page a changé de structure (vérifiez les sélecteurs).");
        }
        writeInitialJson(config, records);

        // 3. Mapping FTM via DynamicFtmMapper
        List<Map<String, Object>> prepared;
        List<FtmEntity> mapped = new ArrayList<>();
        try {
            DocumentFetcher fetcher = documentFetcher(config, files);
            prepared = new RecordPreprocessor(fetcher).process(records, mappingConfig);
            if (fetcher.getDownloaded() + fetcher.getFromCache() + fetcher.getMissing() > 0) {
                log.info("Documents liés : {} téléchargés, {} en cache, {} introuvables", fetcher.getDownloaded(),
                        fetcher.getFromCache(), fetcher.getMissing());
            }
            if (mappingConfig.isApplyAllRules()) {
                for (MappingRule rule : mappingConfig.getRules()) {
                    MappingConfig single = MappingConfig.builder()
                            .name(mappingConfig.getName())
                            .rules(List.of(rule))
                            .build();
                    mapped.addAll(ftmMapper.map(prepared, single));
                }
            } else {
                mapped.addAll(ftmMapper.map(prepared, mappingConfig));
            }
        } catch (IngestionException e) {
            throw e;
        } catch (Exception e) {
            throw new IngestionException(Step.MAPPING,
                    "Erreur lors de l'application du mapping : " + rootMessage(e), e);
        }
        List<FtmEntity> entities = merge(mapped);
        if (entities.isEmpty()) {
            throw new IngestionException(Step.MAPPING, records.size() + " enregistrement(s) lus mais aucune "
                    + "entité FTM produite : aucune règle du mapping ne correspond (conditions ou filtres).");
        }
        log.info("Mapping FTM terminé : {} enregistrements, {} gardés, {} entités", records.size(),
                prepared.size(), entities.size());

        // 4. Enregistrement sous format NDJSON dans ftmOutputPath
        writeNdjson(config, entities);

        Map<String, Long> bySchema = entities.stream()
                .collect(Collectors.groupingBy(FtmEntity::getSchema, TreeMap::new, Collectors.counting()));
        return new GenerationReport(files.size(), records.size(), prepared.size(), entities, bySchema);
    }

    /**
     * Fusionne les entités de même identifiant (ex. la Position commune à tous les députés) :
     * valeurs réunies sans doublon, dans leur ordre d'arrivée.
     */
    static List<FtmEntity> merge(List<FtmEntity> entities) {
        Map<String, FtmEntity> byId = new LinkedHashMap<>();
        for (FtmEntity entity : entities) {
            FtmEntity target = byId.computeIfAbsent(entity.getId(), id -> FtmEntity.builder()
                    .id(id)
                    .schema(entity.getSchema())
                    .build());
            entity.getProperties().forEach((key, values) -> {
                List<String> existing = target.getProperties().computeIfAbsent(key, k -> new ArrayList<>());
                for (String value : values) {
                    if (!existing.contains(value)) {
                        existing.add(value);
                    }
                }
            });
        }
        return new ArrayList<>(byId.values());
    }

    private ConverterOptions readConverterOptions(DataSourceConfig config) {
        String json = config.getConverterOptions();
        if (json == null || json.isBlank()) {
            return ConverterOptions.DEFAULT;
        }
        try {
            return objectMapper.readValue(json, ConverterOptions.class);
        } catch (Exception e) {
            throw new IngestionException(Step.CONFIGURATION,
                    "Options de conversion illisibles (JSON attendu) : " + rootMessage(e), e);
        }
    }

    private MappingConfig readMappingConfig(String path) {
        if (path == null || path.isBlank()) {
            throw new IngestionException(Step.CONFIGURATION, "Aucun fichier de mapping n'est configuré pour cette source.");
        }
        try (InputStream is = resolveInputStream(path)) {
            MappingConfig mappingConfig = objectMapper.readValue(is, MappingConfig.class);
            if (mappingConfig.getRules() == null || mappingConfig.getRules().isEmpty()) {
                throw new IngestionException(Step.CONFIGURATION, "Le fichier de mapping " + path + " ne contient aucune règle.");
            }
            return mappingConfig;
        } catch (IngestionException e) {
            throw e;
        } catch (FileNotFoundException e) {
            throw new IngestionException(Step.CONFIGURATION, "Fichier de mapping introuvable : " + path, e);
        } catch (Exception e) {
            throw new IngestionException(Step.CONFIGURATION,
                    "Fichier de mapping " + path + " invalide : " + rootMessage(e), e);
        }
    }

    /** Un fichier source : son adresse d'origine et son chemin local. */
    private record SourceFile(String origin, String localPath) {
    }

    /**
     * Télécharge (URL) ou copie (chemin local) chaque fichier de externalSourcePath vers localSourcePath.
     * Sans externalSourcePath, lit directement localSourcePath.
     */
    private List<SourceFile> download(DataSourceConfig config) {
        String external = config.getExternalSourcePath();
        String local = config.getLocalSourcePath();
        if (external == null || external.isBlank()) {
            if (local == null || local.isBlank()) {
                throw new IngestionException(Step.CONFIGURATION,
                        "Ni adresse de la source (externalSourcePath) ni fichier local (localSourcePath) configuré.");
            }
            return List.of(new SourceFile(local, local));
        }
        if (local == null || local.isBlank()) {
            throw new IngestionException(Step.CONFIGURATION,
                    "localSourcePath doit être spécifié pour enregistrer le fichier téléchargé");
        }

        List<String> origins = Arrays.stream(external.split("\\s+")).filter(s -> !s.isBlank()).toList();
        List<SourceFile> files = new ArrayList<>();
        for (int i = 0; i < origins.size(); i++) {
            String target = i == 0 ? local : indexedPath(local, i + 1);
            try {
                copyToLocal(origins.get(i), target);
            } catch (IngestionException e) {
                throw e;
            } catch (Exception e) {
                throw new IngestionException(Step.DOWNLOAD,
                        "Échec du téléchargement de " + origins.get(i) + " : " + rootMessage(e), e);
            }
            files.add(new SourceFile(origins.get(i), target));
        }
        return files;
    }

    /**
     * Téléchargeur des documents liés : cache dans le dossier du fichier source ({@code documents/}),
     * et lecture d'abord dans les dossiers des fichiers source locaux (fichiers de test hors ligne).
     */
    private DocumentFetcher documentFetcher(DataSourceConfig config, List<SourceFile> files) {
        Path cacheDir = null;
        if (config.getLocalSourcePath() != null && !config.getLocalSourcePath().isBlank()) {
            Path parent = Paths.get(config.getLocalSourcePath()).toAbsolutePath().getParent();
            cacheDir = parent == null ? null : parent.resolve("documents");
        }
        List<Path> localDirs = new ArrayList<>();
        for (SourceFile file : files) {
            String origin = file.origin();
            if (!origin.startsWith("http://") && !origin.startsWith("https://") && !origin.startsWith("classpath:")) {
                Path parent = Paths.get(origin).toAbsolutePath().getParent();
                if (parent != null && !localDirs.contains(parent)) {
                    localDirs.add(parent);
                }
            }
        }
        return new DocumentFetcher(cacheDir, localDirs);
    }

    /** « data/x/source.html » → « data/x/source-2.html ». */
    static String indexedPath(String path, int index) {
        int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        int dot = path.lastIndexOf('.');
        return dot > slash ? path.substring(0, dot) + "-" + index + path.substring(dot) : path + "-" + index;
    }

    private void copyToLocal(String origin, String localSourcePath) throws IOException, InterruptedException {
        Path localPath = Paths.get(localSourcePath);
        if (localPath.getParent() != null) {
            Files.createDirectories(localPath.getParent());
        }

        // Si externalSourcePath est une URL HTTP/HTTPS
        if (origin.startsWith("http://") || origin.startsWith("https://")) {
            log.info("Téléchargement du fichier depuis {} vers {}", origin, localSourcePath);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(origin))
                    .header("User-Agent", "Mozilla/5.0 (compatible; SwiftCheckAML/1.0)")
                    .timeout(Duration.ofMinutes(5))
                    .GET()
                    .build();

            // Jusqu'à 3 tentatives pour une coupure réseau ou une erreur 5xx ; une erreur 4xx est définitive
            for (int attempt = 1; ; attempt++) {
                try {
                    HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
                    if (response.statusCode() >= 400) {
                        response.body().close();
                        if (response.statusCode() < 500 || attempt == DOWNLOAD_ATTEMPTS) {
                            throw new IngestionException(Step.DOWNLOAD, "Échec du téléchargement depuis " + origin
                                    + ", code HTTP: " + response.statusCode());
                        }
                    } else {
                        try (InputStream body = response.body()) {
                            Files.copy(body, localPath, StandardCopyOption.REPLACE_EXISTING);
                        }
                        break;
                    }
                } catch (IOException e) {
                    if (attempt == DOWNLOAD_ATTEMPTS) {
                        throw e;
                    }
                    log.warn("Téléchargement de {} interrompu (tentative {}/{}) : {}", origin, attempt,
                            DOWNLOAD_ATTEMPTS, e.getMessage());
                }
                Thread.sleep(2000L * attempt);
            }
            log.info("Téléchargement terminé : {} octets", Files.size(localPath));
        } else {
            // Si c'est un chemin de fichier local ou une ressource du classpath
            Path sourceFile = Paths.get(origin.startsWith("classpath:") ? "" : origin);
            if (!origin.startsWith("classpath:") && Files.exists(sourceFile)) {
                if (!sourceFile.toAbsolutePath().equals(localPath.toAbsolutePath())) {
                    Files.copy(sourceFile, localPath, StandardCopyOption.REPLACE_EXISTING);
                }
            } else {
                try (InputStream is = resolveInputStream(origin)) {
                    Files.copy(is, localPath, StandardCopyOption.REPLACE_EXISTING);
                } catch (FileNotFoundException e) {
                    throw new IngestionException(Step.DOWNLOAD, "Fichier source introuvable : " + origin, e);
                }
            }
        }
    }

    private void writeInitialJson(DataSourceConfig config, List<Map<String, Object>> records) {
        if (config.getInitialJsonSourcePath() == null || config.getInitialJsonSourcePath().isBlank()) {
            return;
        }
        try {
            Path initialJsonPath = Paths.get(config.getInitialJsonSourcePath());
            if (initialJsonPath.getParent() != null) {
                Files.createDirectories(initialJsonPath.getParent());
            }
            objectMapper.writeValue(initialJsonPath.toFile(), records);
            log.info("Données sources converties en JSON enregistrées dans : {}", config.getInitialJsonSourcePath());
        } catch (IOException e) {
            throw new IngestionException(Step.CONVERSION, "Impossible d'écrire le JSON intermédiaire "
                    + config.getInitialJsonSourcePath() + " : " + rootMessage(e), e);
        }
    }

    private void writeNdjson(DataSourceConfig config, List<FtmEntity> entities) {
        if (config.getFtmOutputPath() == null || config.getFtmOutputPath().isBlank()) {
            return;
        }
        Path ftmOutputPath = Paths.get(config.getFtmOutputPath());
        try {
            if (ftmOutputPath.getParent() != null) {
                Files.createDirectories(ftmOutputPath.getParent());
            }
            try (BufferedWriter writer = Files.newBufferedWriter(ftmOutputPath, StandardCharsets.UTF_8)) {
                for (FtmEntity entity : entities) {
                    writer.write(entity.toJsonString());
                    writer.newLine();
                }
            }
            log.info("Entités FTM exportées avec succès au format NDJSON dans : {}", config.getFtmOutputPath());
        } catch (IOException e) {
            throw new IngestionException(Step.EXPORT,
                    "Impossible d'écrire le fichier FTM " + ftmOutputPath + " : " + rootMessage(e), e);
        }
    }

    /**
     * Résout un flux d'entrée à partir d'un chemin système ou d'une ressource
     * classpath.
     */
    private InputStream resolveInputStream(String path) throws IOException {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("Le chemin spécifié ne peut pas être nul ou vide");
        }

        // 1. Préfixe classpath
        if (path.startsWith("classpath:")) {
            String cp = path.substring("classpath:".length());
            InputStream is = getClass().getResourceAsStream(cp.startsWith("/") ? cp : "/" + cp);
            if (is != null) {
                return is;
            }
        }

        // 2. Fichier direct sur le système de fichiers
        File file = new File(path);
        if (file.exists() && file.isFile()) {
            return new FileInputStream(file);
        }

        // 3. Ressource dans le Classpath
        InputStream is = getClass().getResourceAsStream(path.startsWith("/") ? path : "/" + path);
        if (is != null) {
            return is;
        }

        throw new FileNotFoundException("Impossible de trouver le fichier : " + path);
    }

    /** Message de la cause première, lisible par l'utilisateur. */
    static String rootMessage(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String message = root.getMessage();
        return message == null || message.isBlank() ? root.getClass().getSimpleName() : message;
    }

    /** Index des entités par identifiant (utilitaire de test et d'aperçu). */
    public static Map<String, FtmEntity> byId(List<FtmEntity> entities) {
        return entities.stream().collect(Collectors.toMap(FtmEntity::getId, Function.identity(), (a, b) -> a,
                LinkedHashMap::new));
    }
}
