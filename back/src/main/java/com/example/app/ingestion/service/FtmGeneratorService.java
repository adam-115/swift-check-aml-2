package com.example.app.ingestion.service;

import com.example.app.core.DataSourceConfig;
import com.example.app.ingestion.converter.CsvSourceConverter;
import com.example.app.ingestion.converter.HtmlSourceConverter;
import com.example.app.ingestion.converter.IDynamicSourceConverter;
import com.example.app.ingestion.converter.JsonSourceConverter;
import com.example.app.ingestion.converter.XlsxSourceConverter;
import com.example.app.ingestion.converter.XmlSourceConverter;
import com.example.app.ingestion.engine.DynamicFtmMapper;
import com.example.app.ingestion.model.FtmEntity;
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
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Service d'orchestration pour la génération FollowTheMoney (FTM) à partir
 * d'une configuration DataSourceConfig.
 * Étapes :
 * 1. Téléchargement depuis externalSourcePath vers localSourcePath (si URL
 * fournie).
 * 2. Conversion dynamique selon sourceFormat vers initialJsonSourcePath (JSON).
 * 3. Mapping FTM via DynamicFtmMapper avec initialJsonSourcePath et
 * mappingFilePath.
 * 4. Export des entités FTM dans ftmOutputPath au format NDJSON.
 */
@Slf4j
@Service
public class FtmGeneratorService {

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
                new HtmlSourceConverter()), new DynamicFtmMapper());
    }

    public FtmGeneratorService(List<IDynamicSourceConverter> converterList, DynamicFtmMapper ftmMapper) {
        this.ftmMapper = ftmMapper;
        this.objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
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
     * @throws Exception en cas d'erreur lors du téléchargement, conversion, mapping
     *                   ou écriture
     */
    public List<FtmEntity> generate(DataSourceConfig config) throws Exception {
        log.info("Démarrage de la génération FTM avec DataSourceConfig: format={}, localSource={}, mapping={}",
                config.getSourceFormat(), config.getLocalSourcePath(), config.getMappingFilePath());

        // 1. Téléchargement depuis externalSourcePath si spécifié
        downloadIfPresent(config.getExternalSourcePath(), config.getLocalSourcePath());

        // 2. Conversion du fichier source selon sourceFormat vers initialJsonSourcePath
        IDynamicSourceConverter converter = converters.get(config.getSourceFormat());
        if (converter == null) {
            throw new IllegalArgumentException(
                    "Aucun convertisseur disponible pour le format : " + config.getSourceFormat());
        }

        List<Map<String, Object>> dynamicRecords;
        try (InputStream localIs = resolveInputStream(config.getLocalSourcePath())) {
            dynamicRecords = converter.convertToDynamicJson(localIs);
        }

        if (config.getInitialJsonSourcePath() != null && !config.getInitialJsonSourcePath().isBlank()) {
            Path initialJsonPath = Paths.get(config.getInitialJsonSourcePath());
            if (initialJsonPath.getParent() != null) {
                Files.createDirectories(initialJsonPath.getParent());
            }
            objectMapper.writeValue(initialJsonPath.toFile(), dynamicRecords);
            log.info("Données sources converties en JSON enregistrées dans : {}", config.getInitialJsonSourcePath());
        }

        // 3. Mapping FTM via DynamicFtmMapper avec initialJsonSourcePath et
        // mappingFilePath
        List<FtmEntity> ftmEntities;
        try (InputStream jsonIs = resolveInputStream(config.getInitialJsonSourcePath() != null
                ? config.getInitialJsonSourcePath()
                : config.getLocalSourcePath());
                InputStream mappingIs = resolveInputStream(config.getMappingFilePath())) {
            ftmEntities = ftmMapper.map(jsonIs, mappingIs);
        }

        log.info("Mapping FTM terminé : {} entités générées", ftmEntities.size());

        // 4. Enregistrement sous format NDJSON dans ftmOutputPath
        if (config.getFtmOutputPath() != null && !config.getFtmOutputPath().isBlank()) {
            Path ftmOutputPath = Paths.get(config.getFtmOutputPath());
            if (ftmOutputPath.getParent() != null) {
                Files.createDirectories(ftmOutputPath.getParent());
            }
            try (BufferedWriter writer = Files.newBufferedWriter(ftmOutputPath, StandardCharsets.UTF_8)) {
                for (FtmEntity entity : ftmEntities) {
                    writer.write(entity.toJsonString());
                    writer.newLine();
                }
            }
            log.info("Entités FTM exportées avec succès au format NDJSON dans : {}", config.getFtmOutputPath());
        }

        return ftmEntities;
    }

    /**
     * Télécharge le fichier externe vers le chemin local si externalSourcePath est
     * renseigné.
     */
    private void downloadIfPresent(String externalSourcePath, String localSourcePath)
            throws IOException, InterruptedException {
        if (externalSourcePath == null || externalSourcePath.isBlank()) {
            return;
        }

        if (localSourcePath == null || localSourcePath.isBlank()) {
            throw new IllegalArgumentException(
                    "localSourcePath doit être spécifié pour enregistrer le fichier téléchargé");
        }

        Path localPath = Paths.get(localSourcePath);
        if (localPath.getParent() != null) {
            Files.createDirectories(localPath.getParent());
        }

        // Si externalSourcePath est une URL HTTP/HTTPS
        if (externalSourcePath.startsWith("http://") || externalSourcePath.startsWith("https://")) {
            log.info("Téléchargement du fichier depuis {} vers {}", externalSourcePath, localSourcePath);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(externalSourcePath))
                    .header("User-Agent", "SwiftCheckAML/1.0")
                    .GET()
                    .build();

            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() >= 400) {
                throw new IllegalStateException("Échec du téléchargement depuis " + externalSourcePath + ", code HTTP: "
                        + response.statusCode());
            }

            try (InputStream body = response.body()) {
                Files.copy(body, localPath, StandardCopyOption.REPLACE_EXISTING);
            }
            log.info("Téléchargement terminé : {} octets", Files.size(localPath));
        } else {
            // Si c'est un chemin de fichier local source
            Path sourceFile = Paths.get(externalSourcePath);
            if (Files.exists(sourceFile) && !sourceFile.equals(localPath)) {
                Files.copy(sourceFile, localPath, StandardCopyOption.REPLACE_EXISTING);
            }
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
}
