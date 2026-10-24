package com.example.app.ingestion.converter;

import com.example.app.ingestion.model.SourceFormat;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Dynamically converts HTML tables or lists (e.g. scraped sanctions portals,
 * official register pages)
 * into dynamic JSON Maps without DTOs.
 */
@Slf4j
@Component
public class HtmlSourceConverter implements IDynamicSourceConverter {

    @Override
    public SourceFormat getSupportedFormat() {
        return SourceFormat.HTML;
    }

    @Override
    public List<Map<String, Object>> convertToDynamicJson(InputStream inputStream) throws Exception {
        Document doc = Jsoup.parse(inputStream, StandardCharsets.UTF_8.name(), "");
        List<Map<String, Object>> records = new ArrayList<>();

        // 1. Look for tables
        Elements tables = doc.select("table");
        if (!tables.isEmpty()) {
            Element table = tables.first();
            Elements rows = table.select("tr");
            if (rows.size() >= 2) {
                // Header row
                Elements headerCols = rows.get(0).select("th");
                if (headerCols.isEmpty()) {
                    headerCols = rows.get(0).select("td");
                }

                List<String> headers = new ArrayList<>();
                for (Element col : headerCols) {
                    headers.add(col.text().trim());
                }

                // Data rows
                for (int i = 1; i < rows.size(); i++) {
                    Elements cols = rows.get(i).select("td");
                    if (cols.isEmpty()) {
                        continue;
                    }
                    Map<String, Object> map = new LinkedHashMap<>();
                    for (int j = 0; j < Math.min(headers.size(), cols.size()); j++) {
                        String header = headers.get(j);
                        String value = cols.get(j).text().trim();
                        if (!header.isEmpty() && !value.isEmpty()) {
                            map.put(header, value);
                        }
                    }
                    if (!map.isEmpty()) {
                        records.add(map);
                    }
                }
                return records;
            }
        }

        // 2. Fallback: look for generic structured sanction elements (e.g.,
        // div[data-entity], .sanction-entry)
        Elements items = doc.select(".sanction-entry, .entity-item, li.entry");
        for (Element item : items) {
            Map<String, Object> map = new LinkedHashMap<>();
            for (Element child : item.children()) {
                String key = !child.className().isEmpty() ? child.className() : child.tagName();
                String val = child.text().trim();
                if (!val.isEmpty()) {
                    map.put(key, val);
                }
            }
            if (!map.isEmpty()) {
                records.add(map);
            }
        }

        return records;
    }
}
