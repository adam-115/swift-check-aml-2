package com.example.app.api.dto;

import com.example.app.core.Completeness;
import com.example.app.core.DataSourceConfig;
import com.example.app.core.ListType;
import com.example.app.core.ParserCoverage;
import com.example.app.core.ParserType;
import com.example.app.ingestion.model.SourceFormat;

import java.util.List;

/**
 * Une source de données avec sa couverture PEP et sa dernière exécution.
 *
 * @param implemented vrai si un fichier de mapping est configuré : la source peut être exécutée
 * @param inCatalog   vrai si la source est définie dans le catalogue (elle peut être réinitialisée)
 * @param customized  vrai si la source a été modifiée dans l'interface (le catalogue ne l'écrase plus)
 */
public record SourceDto(Long id,
                        String sourceName,
                        String label,
                        String description,
                        String attribution,
                        SourceFormat sourceFormat,
                        ParserType parserType,
                        ListType listType,
                        String countryIso,
                        String externalSourcePath,
                        String mappingFilePath,
                        String ftmOutputPath,
                        String converterOptions,
                        Integer retentionMonths,
                        boolean enabled,
                        boolean implemented,
                        boolean inCatalog,
                        boolean customized,
                        List<CoveredFunction> coverage,
                        ExecutionDto lastExecution) {

    /** Fonction PEP couverte par la source. */
    public record CoveredFunction(Long functionId, String categoryCode, String label, Completeness completeness,
                                  boolean includesFormer) {

        public static CoveredFunction of(ParserCoverage c) {
            return new CoveredFunction(c.getPepFunction().getId(), c.getPepFunction().getCategory().getCode(),
                    c.getPepFunction().getLabel(), c.getCompleteness(), c.isIncludesFormer());
        }
    }

    public static SourceDto of(DataSourceConfig s, List<ParserCoverage> coverage, ExecutionDto lastExecution,
                               boolean inCatalog) {
        return new SourceDto(s.getId(), s.getSourceName(), s.getLabel(), s.getDescription(), s.getAttribution(),
                s.getSourceFormat(), s.getParserType(), s.getListType(),
                s.getCountry() != null ? s.getCountry().getIso() : null,
                s.getExternalSourcePath(), s.getMappingFilePath(), s.getFtmOutputPath(), s.getConverterOptions(),
                s.getRetentionMonths(), s.isEnabled(), isImplemented(s), inCatalog, s.isCustomized(),
                coverage.stream().map(CoveredFunction::of).toList(), lastExecution);
    }

    public static boolean isImplemented(DataSourceConfig s) {
        return s.getMappingFilePath() != null && !s.getMappingFilePath().isBlank();
    }
}
