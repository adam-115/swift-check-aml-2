package com.example.app.ingestion.converter;

import com.example.app.ingestion.model.SourceFormat;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Convertit un document PDF en un enregistrement JSON par ligne de texte non vide :
 * {@code {"line": "...", "lineNumber": 12, "page": 1, "previousLine": "...", "nextLine": "..."}}.
 * Les lignes voisines permettent de lire une fonction écrite sous le nom de son titulaire ; le mapping
 * garde les lignes utiles avec des filtres et des expressions régulières.
 */
@Slf4j
@Component
public class PdfSourceConverter implements IDynamicSourceConverter {

    @Override
    public SourceFormat getSupportedFormat() {
        return SourceFormat.PDF;
    }

    @Override
    public List<Map<String, Object>> convertToDynamicJson(InputStream inputStream) throws Exception {
        List<String[]> lines = new ArrayList<>();   // [page, texte]
        try (PDDocument document = Loader.loadPDF(inputStream.readAllBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            for (int page = 1; page <= document.getNumberOfPages(); page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                for (String line : stripper.getText(document).split("\\R")) {
                    String text = line.replace(' ', ' ').replaceAll("\\s+", " ").trim();
                    if (!text.isEmpty()) {
                        lines.add(new String[]{String.valueOf(page), text});
                    }
                }
            }
        }

        List<Map<String, Object>> records = new ArrayList<>(lines.size());
        for (int i = 0; i < lines.size(); i++) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("line", lines.get(i)[1]);
            map.put("lineNumber", i + 1);
            map.put("page", Integer.parseInt(lines.get(i)[0]));
            if (i > 0) {
                map.put("previousLine", lines.get(i - 1)[1]);
            }
            if (i + 1 < lines.size()) {
                map.put("nextLine", lines.get(i + 1)[1]);
            }
            records.add(map);
        }
        return records;
    }
}
