package com.example.app.core.normalizer;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.Year;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Normaliseur pour les dates FTM (ISO-8601 : YYYY-MM-DD, YYYY-MM ou YYYY).
 */
@Component
public class DateNormalizer implements ValueNormalizer {

    public static final String TYPE = "date";

    private static final Pattern YEAR_ONLY_PATTERN = Pattern.compile("^\\d{4}$");
    private static final Pattern YEAR_MONTH_PATTERN = Pattern.compile("^\\d{4}-\\d{2}$");
    private static final Pattern ISO_DATE_PATTERN = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");

    private static final List<DateTimeFormatter> DATE_FORMATTERS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,                          // 2003-07-07
            DateTimeFormatter.ofPattern("yyyy/MM/dd"),                 // 2003/07/07
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),                 // 07/07/2003
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),                 // 07-07-2003
            DateTimeFormatter.ofPattern("MM/dd/yyyy"),                 // 07/28/1937
            DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH), // 7 Jul 2003
            DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH),// Jul 7, 2003
            DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH),// 7 July 2003
            DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRENCH), // 7 juillet 2003
            DateTimeFormatter.ISO_DATE_TIME,                           // 2003-07-07T10:00:00Z
            DateTimeFormatter.ofPattern("d.M.yyyy"),                   // 07.07.2003 (Luxembourg, Allemagne)
            DateTimeFormatter.ofPattern("d/M/yyyy"),                   // 7/7/2003
            caseInsensitive("d-MMM-yy", Locale.ENGLISH),               // 27-Oct-23
            caseInsensitive("d MMMM yyyy", Locale.FRENCH),             // 7 Juillet 2003
            caseInsensitive("d MMMM yyyy", Locale.GERMAN)              // 7. Juli 2003 (après retrait du point)
    );

    private static DateTimeFormatter caseInsensitive(String pattern, Locale locale) {
        return new java.time.format.DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern(pattern)
                .toFormatter(locale);
    }

    @Override
    public String getSupportedType() {
        return TYPE;
    }

    @Override
    public String normalize(String rawValue) {
        if (rawValue == null || rawValue.trim().isEmpty()) {
            return null;
        }

        String trimmed = rawValue.trim();

        // 1. Déjà conforme ISO (YYYY, YYYY-MM ou YYYY-MM-DD)
        if (YEAR_ONLY_PATTERN.matcher(trimmed).matches()
                || YEAR_MONTH_PATTERN.matcher(trimmed).matches()
                || ISO_DATE_PATTERN.matcher(trimmed).matches()) {
            return trimmed;
        }

        // 2. Format partiel MM/YYYY -> YYYY-MM
        if (trimmed.matches("^\\d{2}/\\d{4}$")) {
            String[] parts = trimmed.split("/");
            return parts[1] + "-" + parts[0];
        }

        // 3. Tronquer un horodatage avec heure (ex: "2003-07-07 14:30:00")
        if (trimmed.length() >= 10 && trimmed.charAt(4) == '-' && trimmed.charAt(7) == '-') {
            String prefix = trimmed.substring(0, 10);
            if (ISO_DATE_PATTERN.matcher(prefix).matches()) {
                return prefix;
            }
        }

        // 4. Tentative de parsing complet avec les formats usuels
        // « 1er juillet 2003 » → « 1 juillet 2003 », « 7. Juli 2003 » → « 7 Juli 2003 »
        String candidate = trimmed.replaceFirst("^1er\\b", "1").replaceFirst("^(\\d{1,2})\\. ", "$1 ");
        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                LocalDate date = LocalDate.parse(candidate, formatter);
                return date.format(DateTimeFormatter.ISO_LOCAL_DATE);
            } catch (DateTimeParseException ignored) {
            }
        }

        // 5. En cas d'échec de parsing, retourne la valeur nettoyée
        return trimmed;
    }
}
