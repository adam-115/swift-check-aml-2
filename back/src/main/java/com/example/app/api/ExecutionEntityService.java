package com.example.app.api;

import com.example.app.api.ExecutionService.FileKind;
import com.example.app.api.dto.EntityPage;
import com.example.app.api.dto.EntityPage.EntityItem;
import com.example.app.api.dto.EntityPage.EntityRef;
import com.example.app.core.entities.SourceExecution;
import com.example.app.ingestion.model.FtmEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Entités FTM produites par une exécution, lues dans son fichier FTM archivé (NDJSON). Les lignes de même
 * identifiant décrivent la même entité (ex. une personne répétée à chaque mandat) : elles sont fusionnées, comme le
 * prévoit FollowTheMoney.
 */
@Service
public class ExecutionEntityService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ExecutionService executionService;

    public ExecutionEntityService(ExecutionService executionService) {
        this.executionService = executionService;
    }

    /**
     * Une page d'entités, filtrée par schéma et par texte (libellé ou valeur de propriété).
     *
     * @param page numéro de page, à partir de 0
     */
    public EntityPage page(SourceExecution execution, String schema, String query, int page, int size) {
        Map<String, FtmEntity> all = read(execution);

        Map<String, Long> schemas = new TreeMap<>();
        all.values().forEach(e -> schemas.merge(e.getSchema(), 1L, Long::sum));

        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<FtmEntity> matching = all.values().stream()
                .filter(e -> schema == null || schema.isBlank() || schema.equalsIgnoreCase(e.getSchema()))
                .filter(e -> q.isEmpty() || caption(e, all).toLowerCase(Locale.ROOT).contains(q)
                        || e.getProperties().values().stream().flatMap(List::stream)
                        .anyMatch(v -> v.toLowerCase(Locale.ROOT).contains(q)))
                .sorted(Comparator.comparing(FtmEntity::getSchema)
                        .thenComparing(e -> caption(e, all), String.CASE_INSENSITIVE_ORDER))
                .toList();

        int safeSize = Math.clamp(size, 1, 500);
        int from = Math.min(Math.max(page, 0) * safeSize, matching.size());
        List<FtmEntity> slice = matching.subList(from, Math.min(from + safeSize, matching.size()));

        // Libellés des entités citées par la page (holder, post...), pour afficher des noms plutôt que des hachages
        Map<String, EntityRef> references = new LinkedHashMap<>();
        for (FtmEntity e : slice) {
            for (List<String> values : e.getProperties().values()) {
                for (String v : values) {
                    FtmEntity target = all.get(v);
                    if (target != null && !references.containsKey(v)) {
                        references.put(v, new EntityRef(target.getSchema(), caption(target, all)));
                    }
                }
            }
        }

        List<EntityItem> items = slice.stream()
                .map(e -> new EntityItem(e.getId(), e.getSchema(), caption(e, all), e.getProperties()))
                .toList();
        return new EntityPage(all.size(), matching.size(), Math.max(page, 0), safeSize, schemas, items, references);
    }

    /** Entités du fichier FTM archivé, fusionnées par identifiant, dans l'ordre du fichier. */
    Map<String, FtmEntity> read(SourceExecution execution) {
        Path file = executionService.file(execution, FileKind.FTM).orElse(null);
        Map<String, FtmEntity> byId = new LinkedHashMap<>();
        if (file == null) {
            return byId;
        }
        Map<String, Map<String, Set<String>>> merged = new LinkedHashMap<>();
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                if (line.isBlank()) {
                    continue;
                }
                FtmEntity e = MAPPER.readValue(line, FtmEntity.class);
                if (!byId.containsKey(e.getId())) {
                    byId.put(e.getId(), FtmEntity.builder().id(e.getId()).schema(e.getSchema()).build());
                }
                Map<String, Set<String>> props = merged.computeIfAbsent(e.getId(), k -> new LinkedHashMap<>());
                if (e.getProperties() != null) {
                    e.getProperties().forEach((k, values) ->
                            props.computeIfAbsent(k, x -> new LinkedHashSet<>()).addAll(values));
                }
            }
        } catch (IOException ex) {
            throw new UncheckedIOException("Lecture du fichier FTM impossible : " + file, ex);
        }
        merged.forEach((id, props) -> props.forEach((k, values) -> byId.get(id).addProperties(k, values)));
        return byId;
    }

    /** Libellé d'une entité : son nom, ou pour un lien (Occupancy...) « titulaire · poste ». */
    private static String caption(FtmEntity e, Map<String, FtmEntity> all) {
        Map<String, List<String>> p = e.getProperties();
        for (String key : List.of("name", "caption", "title")) {
            if (p.containsKey(key) && !p.get(key).isEmpty()) {
                return p.get(key).get(0);
            }
        }
        List<String> parts = new ArrayList<>();
        for (String key : List.of("holder", "post", "subject", "object", "owner", "asset", "member", "organization")) {
            if (p.containsKey(key) && !p.get(key).isEmpty()) {
                FtmEntity target = all.get(p.get(key).get(0));
                if (target != null && target != e) {
                    parts.add(caption(target, all));
                }
            }
        }
        return parts.isEmpty() ? e.getSchema() + " " + e.getId().substring(0, Math.min(8, e.getId().length()))
                : String.join(" · ", parts);
    }
}
