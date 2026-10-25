package com.example.app.ingestion.converter;

import com.example.app.ingestion.model.SourceFormat;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.io.InputStream;
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
        return convertToDynamicJson(inputStream, ConverterOptions.DEFAULT);
    }

    /**
     * Avec {@link ConverterOptions.HtmlExtraction#getRecordSelector()} : un enregistrement par élément
     * sélectionné, champs lus par sélecteurs CSS. Sinon : premier tableau de la page, ou listes génériques.
     */
    @Override
    public List<Map<String, Object>> convertToDynamicJson(InputStream inputStream, ConverterOptions options)
            throws Exception {
        ConverterOptions opts = options != null ? options : ConverterOptions.DEFAULT;
        Document doc = Jsoup.parse(inputStream, opts.charset().name(),
                opts.getBaseUrl() != null && opts.getBaseUrl().startsWith("http") ? opts.getBaseUrl() : "");
        ConverterOptions.HtmlExtraction html = opts.getHtml();
        if (html != null && html.getRecordSelector() != null && !html.getRecordSelector().isBlank()) {
            return extract(doc, html);
        }
        return convertGeneric(doc);
    }

    private List<Map<String, Object>> extract(Document doc, ConverterOptions.HtmlExtraction html) {
        List<Map<String, Object>> records = new ArrayList<>();
        for (Element element : doc.select(html.getRecordSelector())) {
            Map<String, Object> map = new LinkedHashMap<>();
            for (Map.Entry<String, String> field : html.getFields().entrySet()) {
                Object value = readField(element, field.getValue());
                if (value != null) {
                    map.put(field.getKey(), value);
                }
            }
            if (!map.isEmpty()) {
                records.add(map);
            }
        }
        return records;
    }

    private static int matchingParenthesis(String expr, int open) {
        int depth = 0;
        for (int i = open; i < expr.length(); i++) {
            char c = expr.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')' && --depth == 0) {
                return i;
            }
        }
        return -1;
    }

    /** Élément correspondant à {@code css} qui précède {@code element} dans la page, le plus proche. */
    private static Element previous(Element element, String css) {
        for (Element current = element; current != null; current = current.parent()) {
            for (Element sibling = current.previousElementSibling(); sibling != null;
                 sibling = sibling.previousElementSibling()) {
                if (sibling.is(css)) {
                    return sibling;
                }
                Elements inside = sibling.select(css);
                if (!inside.isEmpty()) {
                    return inside.last();
                }
            }
        }
        return null;
    }

    /** Élément correspondant à {@code css} qui suit {@code element} parmi ses voisins, le plus proche. */
    private static Element following(Element element, String css) {
        for (Element sibling = element.nextElementSibling(); sibling != null; sibling = sibling.nextElementSibling()) {
            if (sibling.is(css)) {
                return sibling;
            }
            Elements inside = sibling.select(css);
            if (!inside.isEmpty()) {
                return inside.first();
            }
        }
        return null;
    }

    /** Lit un champ selon la syntaxe décrite dans {@link ConverterOptions.HtmlExtraction}. */
    static Object readField(Element element, String spec) {
        if (spec == null || spec.isBlank()) {
            return null;
        }
        boolean all = spec.startsWith("all:");
        String expr = all ? spec.substring(4) : spec;

        // closest(css) : ancêtre ; prev(css) / next(css) : élément précédent / suivant le plus proche
        while (expr.startsWith("closest(") || expr.startsWith("prev(") || expr.startsWith("next(")) {
            String axis = expr.substring(0, expr.indexOf('('));
            int open = expr.indexOf('(');
            int close = matchingParenthesis(expr, open);
            if (close < 0) {
                return null;
            }
            String css = expr.substring(open + 1, close).trim();
            element = switch (axis) {
                case "closest" -> element.closest(css);
                case "prev" -> previous(element, css);
                default -> following(element, css);
            };
            if (element == null) {
                return null;
            }
            expr = expr.substring(close + 1).trim();
        }

        String selector = expr;
        String attribute = null;
        int at = expr.lastIndexOf('@');
        if (at >= 0 && !expr.substring(at).contains("]")) {
            selector = expr.substring(0, at).trim();
            attribute = expr.substring(at + 1).trim();
        }

        List<Element> targets;
        if (selector.isEmpty() || selector.equals(":self") || selector.equals(":ownText")) {
            targets = List.of(element);
        } else {
            targets = element.select(selector);
        }

        List<String> values = new ArrayList<>();
        for (Element target : targets) {
            String value;
            if (attribute != null) {
                // Seuls les liens sont rendus absolus : « content » d'une balise meta reste du texte
                boolean link = attribute.equals("href") || attribute.equals("src");
                value = link && !target.absUrl(attribute).isEmpty() ? target.absUrl(attribute) : target.attr(attribute);
            } else if (selector.equals(":ownText")) {
                value = target.ownText();
            } else {
                value = target.text();
            }
            value = value == null ? "" : value.replace(' ', ' ').replaceAll("\\s+", " ").trim();
            if (!value.isEmpty()) {
                values.add(value);
                if (!all) {
                    break;
                }
            }
        }
        if (values.isEmpty()) {
            return null;
        }
        return all ? values : values.get(0);
    }

    private List<Map<String, Object>> convertGeneric(Document doc) {
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
