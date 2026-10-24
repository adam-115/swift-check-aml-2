package com.example.app.ingestion.converter;

import com.example.app.ingestion.model.SourceFormat;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Dynamically converts XLSX Excel sheets into dynamic JSON Maps.
 * First row is treated as column headers.
 */
@Slf4j
@Component
public class XlsxSourceConverter implements IDynamicSourceConverter {

    private final DataFormatter dataFormatter = new DataFormatter();

    @Override
    public SourceFormat getSupportedFormat() {
        return SourceFormat.XLSX;
    }

    @Override
    public List<Map<String, Object>> convertToDynamicJson(InputStream inputStream) throws Exception {
        List<Map<String, Object>> records = new ArrayList<>();

        try (Workbook workbook = WorkbookFactory.create(inputStream)) {
            if (workbook.getNumberOfSheets() == 0) {
                return List.of();
            }

            Sheet sheet = workbook.getSheetAt(0);
            if (sheet.getPhysicalNumberOfRows() < 2) {
                return List.of();
            }

            // Read header row
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                return List.of();
            }

            List<String> headers = new ArrayList<>();
            for (int cn = 0; cn < headerRow.getLastCellNum(); cn++) {
                Cell cell = headerRow.getCell(cn);
                String header = (cell != null) ? dataFormatter.formatCellValue(cell).trim() : "";
                headers.add(header);
            }

            // Read data rows
            for (int rn = 1; rn <= sheet.getLastRowNum(); rn++) {
                Row row = sheet.getRow(rn);
                if (row == null) {
                    continue;
                }

                Map<String, Object> map = new LinkedHashMap<>();
                for (int cn = 0; cn < headers.size(); cn++) {
                    String header = headers.get(cn);
                    if (header == null || header.isEmpty()) {
                        continue;
                    }
                    Cell cell = row.getCell(cn);
                    if (cell != null) {
                        String val = dataFormatter.formatCellValue(cell).trim();
                        if (!val.isEmpty()) {
                            map.put(header, val);
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
