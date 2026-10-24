package com.example.app.ingestion.converter;

import com.example.app.ingestion.model.SourceFormat;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Dynamically converts XML fragments or full XML sanction lists (e.g. OFAC SDN,
 * UN lists)
 * into dynamic JSON Maps without source-specific DTOs.
 */
@Slf4j
@Component
public class XmlSourceConverter implements IDynamicSourceConverter {

    private final XmlMapper xmlMapper;
    private final ObjectMapper objectMapper;

    public XmlSourceConverter() {
        this.xmlMapper = new XmlMapper();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public SourceFormat getSupportedFormat() {
        return SourceFormat.XML;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> convertToDynamicJson(InputStream inputStream) throws Exception {
        JsonNode rootNode = xmlMapper.readTree(inputStream);
        if (rootNode == null || rootNode.isEmpty()) {
            return Collections.emptyList();
        }

        // Convert JsonNode to generic Map
        Map<String, Object> rawMap = objectMapper.convertValue(rootNode, new TypeReference<Map<String, Object>>() {
        });
        if (rawMap == null || rawMap.isEmpty()) {
            return Collections.emptyList();
        }

        // Check if root contains repeating record elements (e.g.
        // <sdnList><sdnEntry>...</sdnEntry></sdnList>)
        List<Map<String, Object>> extractedRecords = new ArrayList<>();
        for (Map.Entry<String, Object> entry : rawMap.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof Map<?, ?> itemMap) {
                        extractedRecords.add((Map<String, Object>) itemMap);
                    }
                }
            }
        }

        // If repeating records were found at root, return them
        if (!extractedRecords.isEmpty()) {
            return extractedRecords;
        }

        // Otherwise, the fragment itself is the single entity record
        return List.of(rawMap);
    }
}
