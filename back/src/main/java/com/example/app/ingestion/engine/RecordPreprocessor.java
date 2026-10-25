package com.example.app.ingestion.engine;

import com.example.app.core.DerivedField;
import com.example.app.core.MappingConfig;
import com.example.app.core.RecordExplode;
import com.example.app.core.RecordFilter;
import com.example.app.core.normalizer.DateNormalizer;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;

import java.text.Normalizer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Prépare les enregistrements JSON avant {@link DynamicFtmMapper} : ajoute les champs calculés
 * ({@link MappingConfig#getDerivedFields()}) puis écarte ceux qui ne passent pas les filtres
 * ({@link MappingConfig#getRecordFilters()}). Les enregistrements d'entrée ne sont pas modifiés.
 */
public class RecordPreprocessor {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([^{}]+)}");

    private final Configuration jsonPathConfig = Configuration.defaultConfiguration()
            .addOptions(Option.DEFAULT_PATH_LEAF_TO_NULL, Option.SUPPRESS_EXCEPTIONS);
    private final DateNormalizer dateNormalizer = new DateNormalizer();

    private final DocumentFetcher fetcher;

    public RecordPreprocessor() {
        this(null);
    }

    /** @param fetcher téléchargeur des documents liés (champs {@code fetch}) ; {@code null} : aucun */
    public RecordPreprocessor(DocumentFetcher fetcher) {
        this.fetcher = fetcher;
    }

    public List<Map<String, Object>> process(List<Map<String, Object>> records, MappingConfig config) {
        List<DerivedField> fields = config.getDerivedFields() != null ? config.getDerivedFields() : List.of();
        List<RecordFilter> filters = config.getRecordFilters() != null ? config.getRecordFilters() : List.of();
        RecordExplode explode = config.getExplode();

        // Champs calculés avant le découpage (option « after » du découpage), puis découpage, puis les autres
        List<DerivedField> before = List.of();
        List<DerivedField> after = fields;
        if (explode != null && explode.getAfter() != null) {
            int index = -1;
            for (int i = 0; i < fields.size(); i++) {
                if (explode.getAfter().equals(fields.get(i).getName())) {
                    index = i;
                }
            }
            if (index < 0) {
                throw new IllegalArgumentException("Découpage après « " + explode.getAfter()
                        + " » : aucun champ calculé de ce nom.");
            }
            before = fields.subList(0, index + 1);
            after = fields.subList(index + 1, fields.size());
        }

        List<Map<String, Object>> input = records;
        if (!before.isEmpty()) {
            List<Map<String, Object>> enriched = new ArrayList<>(records.size());
            for (Map<String, Object> source : records) {
                Map<String, Object> record = new LinkedHashMap<>(source);
                applyFields(record, before);
                enriched.add(record);
            }
            input = enriched;
        }
        if (explode != null) {
            input = explode(input, explode);
        }
        if (after.isEmpty() && filters.isEmpty()) {
            return input;
        }

        List<Map<String, Object>> result = new ArrayList<>(input.size());
        for (Map<String, Object> source : input) {
            Map<String, Object> record = new LinkedHashMap<>(source);
            applyFields(record, after);
            if (filters.stream().allMatch(filter -> accept(record, filter))) {
                result.add(record);
            }
        }
        return result;
    }

    private void applyFields(Map<String, Object> record, List<DerivedField> fields) {
        for (DerivedField field : fields) {
            String value = compute(record, field);
            if (value != null && !value.isBlank()) {
                if (field.getSplit() != null && !field.getSplit().isEmpty()) {
                    List<String> parts = new ArrayList<>();
                    for (String part : value.split(field.getSplit())) {
                        if (!part.isBlank()) {
                            parts.add(part.trim());
                        }
                    }
                    record.put(field.getName(), parts);
                } else {
                    record.put(field.getName(), value);
                }
            }
        }
    }

    /** Une copie de l'enregistrement par correspondance de l'expression (voir {@link RecordExplode}). */
    List<Map<String, Object>> explode(List<Map<String, Object>> records, RecordExplode explode) {
        if (explode.getPaths() != null && !explode.getPaths().isEmpty()) {
            return explodeArrays(records, explode);
        }
        Pattern pattern = Pattern.compile(explode.getRegex(), Pattern.DOTALL);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> record : records) {
            String value = read(record, explode.getFrom());
            if (value == null) {
                continue;
            }
            Matcher matcher = pattern.matcher(value);
            while (matcher.find()) {
                Map<String, Object> copy = new LinkedHashMap<>(record);
                copy.put(explode.getAs(), matcher.group().trim());
                for (int g = 1; g <= matcher.groupCount(); g++) {
                    if (matcher.group(g) != null) {
                        copy.put(explode.getAs() + "_" + g, matcher.group(g).trim());
                    }
                }
                result.add(copy);
            }
        }
        return result;
    }

    private List<Map<String, Object>> explodeArrays(List<Map<String, Object>> records, RecordExplode explode) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> record : records) {
            for (String path : explode.getPaths()) {
                Object items = JsonPath.using(jsonPathConfig).parse(record).read(path);
                if (!(items instanceof Iterable<?> iterable)) {
                    continue;
                }
                for (Object item : iterable) {
                    if (item instanceof Map<?, ?>) {
                        Map<String, Object> copy = new LinkedHashMap<>(record);
                        copy.put(explode.getAs(), item);
                        copy.put(explode.getAs() + "_path", path);
                        result.add(copy);
                    }
                }
            }
        }
        return result;
    }

    String compute(Map<String, Object> record, DerivedField field) {
        String value;
        if (field.getFetch() != null) {
            String url = read(record, field.getFetch());
            String text = fetcher != null ? fetcher.fetchText(url) : null;
            return text == null || text.isBlank() ? field.getDefaultValue() : text;
        }
        if (field.getTemplate() != null) {
            value = fillTemplate(record, field.getTemplate(), field.isRequireAll());
        } else {
            value = field.getPick() == null || field.getPick().equalsIgnoreCase("first")
                    ? read(record, field.getFrom())
                    : pick(readAll(record, field.getFrom()), field.getPick());
            if (value != null && field.getRegex() != null) {
                value = applyRegex(value, field);
            }
        }
        if (value != null) {
            value = value.replaceAll("\\s+", " ").trim();
            if (field.isSlug()) {
                value = slug(value);
            } else if (field.isLowercase()) {
                value = value.toLowerCase();
            }
        }
        if (value == null || value.isBlank()) {
            return field.getDefaultValue();
        }
        return value;
    }

    boolean accept(Map<String, Object> record, RecordFilter filter) {
        String value = read(record, filter.getPath());
        boolean empty = value == null || value.isBlank();
        String expected = filter.getValue();
        return switch (filter.getOperator()) {
            case NOT_EMPTY -> !empty;
            case EQUALS -> !empty && value.trim().equalsIgnoreCase(expected);
            case NOT_EQUALS -> empty || !value.trim().equalsIgnoreCase(expected);
            case MATCHES -> !empty && Pattern.compile(expected, Pattern.CASE_INSENSITIVE).matcher(value).find();
            case NOT_MATCHES -> empty || !Pattern.compile(expected, Pattern.CASE_INSENSITIVE).matcher(value).find();
            case WITHIN_YEARS -> empty || isWithinYears(value, Integer.parseInt(expected));
        };
    }

    /** Valeur d'un chemin JsonPath ({@code $.a.b}) ou d'un champ ({@code a}) ; première valeur d'une liste. */
    String read(Map<String, Object> record, String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        if (path.contains("||")) {     // « a||b » : première valeur non vide
            for (String alternative : path.split("\\|\\|")) {
                String value = read(record, alternative.trim());
                if (value != null && !value.isBlank()) {
                    return value;
                }
            }
            return null;
        }
        Object value = path.startsWith("$")
                ? JsonPath.using(jsonPathConfig).parse(record).read(path)
                : record.get(path);
        return firstValue(value);
    }

    /** Toutes les valeurs non vides d'un chemin, dans l'ordre. */
    List<String> readAll(Map<String, Object> record, String path) {
        List<String> values = new ArrayList<>();
        if (path == null || path.isBlank()) {
            return values;
        }
        Object value = path.startsWith("$")
                ? JsonPath.using(jsonPathConfig).parse(record).read(path)
                : record.get(path);
        collect(value, values);
        return values;
    }

    private void collect(Object value, List<String> values) {
        if (value instanceof Iterable<?> iterable) {
            iterable.forEach(item -> collect(item, values));
        } else if (value != null && !(value instanceof Map<?, ?>)) {
            String str = Objects.toString(value).trim();
            if (!str.isEmpty()) {
                values.add(str);
            }
        }
    }

    private static String pick(List<String> values, String mode) {
        if (values.isEmpty()) {
            return null;
        }
        return switch (mode.toLowerCase()) {
            case "last" -> values.get(values.size() - 1);
            case "max" -> values.stream().max(String::compareTo).orElse(null);
            case "min" -> values.stream().min(String::compareTo).orElse(null);
            default -> values.get(0);
        };
    }

    private String firstValue(Object value) {
        if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                String str = firstValue(item);
                if (str != null && !str.isBlank()) {
                    return str;
                }
            }
            return null;
        }
        if (value instanceof Map<?, ?>) {
            return null;
        }
        return value == null ? null : Objects.toString(value).trim();
    }

    private String fillTemplate(Map<String, Object> record, String template, boolean requireAll) {
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String value = read(record, matcher.group(1).trim());
            if (requireAll && (value == null || value.isBlank())) {
                return null;
            }
            matcher.appendReplacement(sb, Matcher.quoteReplacement(value == null ? "" : value));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private String applyRegex(String value, DerivedField field) {
        Pattern pattern = Pattern.compile(field.getRegex(), Pattern.DOTALL);   // (?i) dans le motif pour ignorer la casse
        if (field.getReplacement() != null) {
            return pattern.matcher(value).replaceAll(field.getReplacement());
        }
        Matcher matcher = pattern.matcher(value);
        if (!matcher.find()) {
            return null;
        }
        int group = field.getGroup() != null ? field.getGroup() : (matcher.groupCount() >= 1 ? 1 : 0);
        return matcher.group(group);
    }

    private boolean isWithinYears(String rawDate, int years) {
        String iso = dateNormalizer.normalize(rawDate);
        if (iso == null || !iso.matches("^\\d{4}(-\\d{2}(-\\d{2})?)?$")) {
            return true;   // date illisible : on garde la personne plutôt que de la perdre
        }
        LocalDate date = switch (iso.length()) {
            case 4 -> LocalDate.of(Integer.parseInt(iso), 12, 31);
            case 7 -> LocalDate.parse(iso + "-01").plusMonths(1).minusDays(1);
            default -> LocalDate.parse(iso);
        };
        return !date.isBefore(LocalDate.now().minusYears(years));
    }

    static String slug(String value) {
        String ascii = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return ascii.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }
}
