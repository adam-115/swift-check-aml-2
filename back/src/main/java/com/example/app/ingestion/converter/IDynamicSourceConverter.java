package com.example.app.ingestion.converter;

import com.example.app.ingestion.model.SourceFormat;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Strategy interface for dynamically converting heterogeneous AML/KYC source
 * formats
 * (XML, JSON, CSV, XLSX, HTML) into dynamic, schema-less JSON representations
 * ("Zero-DTO").
 */
public interface IDynamicSourceConverter {

    /**
     * The source format handled by this converter.
     */
    SourceFormat getSupportedFormat();

    /**
     * Converts raw stream data into a list of generic dynamic JSON records (Maps).
     */
    List<Map<String, Object>> convertToDynamicJson(InputStream inputStream) throws Exception;

    /**
     * Convenience method converting a raw text string into dynamic JSON records.
     */
    default List<Map<String, Object>> convertToDynamicJson(String content) throws Exception {
        if (content == null || content.trim().isEmpty()) {
            return List.of();
        }
        try (InputStream is = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8))) {
            return convertToDynamicJson(is);
        }
    }
}
