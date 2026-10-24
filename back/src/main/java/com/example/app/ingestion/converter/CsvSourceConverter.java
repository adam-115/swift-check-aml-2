package com.example.app.ingestion.converter;

import com.example.app.ingestion.model.SourceFormat;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Dynamically converts CSV sanction lists (e.g. OFAC consolidated CSV, EU CSV)
 * into dynamic JSON Maps based on column headers.
 */
@Slf4j
@Component
public class CsvSourceConverter implements IDynamicSourceConverter {

    @Override
    public SourceFormat getSupportedFormat() {
        return SourceFormat.CSV;
    }

    @Override
    public List<Map<String, Object>> convertToDynamicJson(InputStream inputStream) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
        reader.mark(4096);
        String firstLine = reader.readLine();
        reader.reset();

        if (firstLine == null || firstLine.trim().isEmpty()) {
            return List.of();
        }

        // Auto-detect delimiter: semicolon, tab, or comma
        char delimiter = ',';
        if (firstLine.contains(";") && !firstLine.contains(",")) {
            delimiter = ';';
        } else if (firstLine.contains("\t")) {
            delimiter = '\t';
        }

        CSVFormat csvFormat = CSVFormat.DEFAULT.builder()
                .setDelimiter(delimiter)
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreHeaderCase(true)
                .setTrim(true)
                .build();

        List<Map<String, Object>> records = new ArrayList<>();
        try (CSVParser parser = new CSVParser(reader, csvFormat)) {
            Map<String, Integer> headerMap = parser.getHeaderMap();
            for (CSVRecord csvRecord : parser) {
                Map<String, Object> map = new LinkedHashMap<>();
                for (String header : headerMap.keySet()) {
                    if (header != null && !header.isEmpty()) {
                        String value = csvRecord.get(header);
                        if (value != null && !value.trim().isEmpty()) {
                            map.put(header, value.trim());
                        }
                    }
                }
                if (!map.isEmpty()) {
                    records.add(map);
                }
            }
        }

        return records;
    }
}
