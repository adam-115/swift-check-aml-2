package com.example.app.ingestion.service;

import com.example.app.ingestion.converter.IDynamicSourceConverter;
import com.example.app.ingestion.engine.DynamicFtmMapper;
import com.example.app.ingestion.model.FragmentMappingRule;
import com.example.app.ingestion.model.FtmEntity;
import com.example.app.ingestion.model.IngestionConfig;
import com.example.app.ingestion.model.SourceFormat;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedWriter;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * High-performance orchestrator for zero-DTO dynamic data ingestion,
 * mapping heterogeneous sanction feeds (XML, JSON, CSV, XLSX, HTML)
 * into FollowTheMoney (FTM) NDJSON exports.
 */
@Slf4j
@Service
public class DynamicIngestionService {

    private final Map<SourceFormat, IDynamicSourceConverter> converters = new EnumMap<>(SourceFormat.class);
    private final DynamicFtmMapper ftmMapper;
    private final ObjectMapper objectMapper;

    public DynamicIngestionService(List<IDynamicSourceConverter> converterList, DynamicFtmMapper ftmMapper) {
        this.ftmMapper = ftmMapper;
        this.objectMapper = new ObjectMapper();
        for (IDynamicSourceConverter converter : converterList) {
            converters.put(converter.getSupportedFormat(), converter);
        }
    }

    /**
     * Converts raw input of a given format and maps it directly to a list of FTM
     * entities.
     */
    public List<FtmEntity> ingestAndMap(InputStream inputStream, SourceFormat format, FragmentMappingRule rule)
            throws Exception {
        IDynamicSourceConverter converter = converters.get(format);
        if (converter == null) {
            throw new IllegalArgumentException("Unsupported source format: " + format);
        }

        List<Map<String, Object>> sourceJsonRecords = converter.convertToDynamicJson(inputStream);
        log.info("Converted {} raw records from format {}", sourceJsonRecords.size(), format);

        List<FtmEntity> ftmEntities = new ArrayList<>(sourceJsonRecords.size());
        for (Map<String, Object> jsonSourceRecord : sourceJsonRecords) {
            FtmEntity entity = ftmMapper.map(jsonSourceRecord, rule);
            if (entity != null) {
                ftmEntities.add(entity);
            }
        }
        return ftmEntities;
    }

    /**
     * Streams the converted FTM entities as NDJSON directly into an OutputStream.
     * Crucial for large sanction lists (OFAC, OpenSanctions) to avoid high memory
     * pressure.
     */
    public long streamToNdjson(InputStream inputStream, SourceFormat format, FragmentMappingRule rule,
            OutputStream outputStream) throws Exception {
        IDynamicSourceConverter converter = converters.get(format);
        if (converter == null) {
            throw new IllegalArgumentException("Unsupported source format: " + format);
        }

        List<Map<String, Object>> rawRecords = converter.convertToDynamicJson(inputStream);
        long count = 0;

        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8))) {
            for (Map<String, Object> record : rawRecords) {
                FtmEntity entity = ftmMapper.map(record, rule);
                if (entity != null) {
                    writer.write(entity.toJsonString());
                    writer.newLine();
                    count++;
                }
            }
            writer.flush();
        }

        log.info("Streamed {} FTM entities as NDJSON", count);
        return count;
    }

    /**
     * Loads a configuration-driven IngestionConfig from JSON content or stream.
     */
    public IngestionConfig parseConfigFromJson(String jsonContent) throws Exception {
        return objectMapper.readValue(jsonContent, IngestionConfig.class);
    }

    public IngestionConfig parseConfigFromJson(InputStream inputStream) throws Exception {
        return objectMapper.readValue(inputStream, IngestionConfig.class);
    }
}
