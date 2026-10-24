package com.example.app.ingestion.api;

import com.example.app.ingestion.api.dto.IngestionRequest;
import com.example.app.ingestion.config.IngestionRulePresets;
import com.example.app.ingestion.model.FragmentMappingRule;
import com.example.app.ingestion.model.FtmEntity;
import com.example.app.ingestion.model.IngestionConfig;
import com.example.app.ingestion.model.SourceFormat;
import com.example.app.ingestion.service.DynamicIngestionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

/**
 * REST API exposing AML/KYC dynamic ingestion, mapping and NDJSON exports.
 */
@Slf4j
@RestController
@RequestMapping("/api/ingestion")
@RequiredArgsConstructor
public class IngestionController {

    private final DynamicIngestionService ingestionService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @GetMapping("/presets")
    public ResponseEntity<IngestionConfig> getStandardConfig() {
        return ResponseEntity.ok(IngestionRulePresets.standardConfig());
    }

    /**
     * Ingests a text fragment (XML, JSON, CSV, HTML), converts it dynamically without DTOs,
     * and maps it to FollowTheMoney FtmEntity list.
     */
    @PostMapping(value = "/map", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<FtmEntity>> mapFragment(@RequestBody IngestionRequest request) throws Exception {
        if (request.getContent() == null || request.getRule() == null) {
            return ResponseEntity.badRequest().build();
        }
        SourceFormat format = request.getFormat() != null ? request.getFormat() : SourceFormat.JSON;
        try (InputStream is = new ByteArrayInputStream(request.getContent().getBytes(StandardCharsets.UTF_8))) {
            List<FtmEntity> entities = ingestionService.ingestAndMap(is, format, request.getRule());
            return ResponseEntity.ok(entities);
        }
    }

    /**
     * Directly returns the NDJSON representation as plain text / application/x-ndjson.
     */
    @PostMapping(value = "/ndjson", consumes = MediaType.APPLICATION_JSON_VALUE, produces = "application/x-ndjson")
    public ResponseEntity<String> exportNdjsonText(@RequestBody IngestionRequest request) throws Exception {
        if (request.getContent() == null || request.getRule() == null) {
            return ResponseEntity.badRequest().build();
        }
        SourceFormat format = request.getFormat() != null ? request.getFormat() : SourceFormat.JSON;
        try (InputStream is = new ByteArrayInputStream(request.getContent().getBytes(StandardCharsets.UTF_8))) {
            List<FtmEntity> entities = ingestionService.ingestAndMap(is, format, request.getRule());
            String ndjson = entities.stream()
                    .map(FtmEntity::toJsonString)
                    .collect(Collectors.joining("\n"));
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"export.ndjson\"")
                    .body(ndjson);
        }
    }

    /**
     * File upload endpoint streaming the converted results directly to NDJSON download.
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = "application/x-ndjson")
    public ResponseEntity<StreamingResponseBody> uploadAndExportNdjson(
            @RequestParam("file") MultipartFile file,
            @RequestParam("format") SourceFormat format,
            @RequestParam(value = "ruleJson", required = false) String ruleJson) throws Exception {

        FragmentMappingRule rule;
        if (ruleJson != null && !ruleJson.trim().isEmpty()) {
            rule = objectMapper.readValue(ruleJson, FragmentMappingRule.class);
        } else {
            // Default preset matching format
            rule = (format == SourceFormat.CSV)
                    ? IngestionRulePresets.csvSanctionsRule()
                    : IngestionRulePresets.ofacSdnPersonRule();
        }

        StreamingResponseBody responseBody = outputStream -> {
            try (InputStream is = file.getInputStream()) {
                ingestionService.streamToNdjson(is, format, rule, outputStream);
            } catch (Exception e) {
                log.error("Error streaming NDJSON", e);
                throw new RuntimeException(e);
            }
        };

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.getName() + ".ndjson\"")
                .contentType(MediaType.parseMediaType("application/x-ndjson"))
                .body(responseBody);
    }
}
