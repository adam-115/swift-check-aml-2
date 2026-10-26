package com.example.app.api.dto;

import com.example.app.core.Completeness;
import com.example.app.core.ListType;
import com.example.app.core.ParserType;
import com.example.app.core.entities.DataSourceConfig;
import com.example.app.core.entities.ParserCoverage;
import com.example.app.core.entities.PepFunction;
import com.example.app.ingestion.model.SourceFormat;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Une source de données et les fonctions PEP qu'elle couvre.
 */
public record SourceDto(Long id,
                        String sourceName,
                        String label,
                        String description,
                        String attribution,
                        SourceFormat sourceFormat,
                        ListType listType,
                        ParserType parserType,
                        String countryIso,
                        Long pepFunctionId,
                        String pepFunctionLabel,
                        String pepFunctionCategoryCode,
                        Integer retentionMonths,
                        boolean enabled,
                        String externalSourcePath,
                        String localSourcePath,
                        String initialJsonSourcePath,
                        String mappingFilePath,
                        String ftmOutputPath,
                        LocalDateTime createdAt,
                        LocalDateTime updatedAt,
                        List<CoveredFunction> coverage) {

    /** Fonction PEP couverte par la source. */
    public record CoveredFunction(Long functionId, String categoryCode, String label, Completeness completeness,
                                  boolean includesFormer) {

        public static CoveredFunction of(ParserCoverage c) {
            return new CoveredFunction(c.getPepFunction().getId(), c.getPepFunction().getCategory().getCode(),
                    c.getPepFunction().getLabel(), c.getCompleteness(), c.isIncludesFormer());
        }
    }

    public static SourceDto of(DataSourceConfig s, List<ParserCoverage> coverage) {
        PepFunction main = s.getPepFunction();
        return new SourceDto(s.getId(), s.getSourceName(), s.getLabel(), s.getDescription(), s.getAttribution(),
                s.getSourceFormat(), s.getListType(), s.getParserType(),
                s.getCountry() != null ? s.getCountry().getIso() : null,
                main != null ? main.getId() : null, main != null ? main.getLabel() : null,
                main != null ? main.getCategory().getCode() : null, s.getRetentionMonths(), s.isEnabled(),
                s.getExternalSourcePath(), s.getLocalSourcePath(), s.getInitialJsonSourcePath(),
                s.getMappingFilePath(), s.getFtmOutputPath(), s.getCreatedAt(), s.getUpdatedAt(),
                coverage.stream().map(CoveredFunction::of).toList());
    }
}
