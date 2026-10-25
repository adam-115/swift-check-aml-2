package com.example.app.api;

import com.example.app.api.dto.CoverageDto;
import com.example.app.api.dto.SourceDto;
import com.example.app.core.Completeness;
import com.example.app.core.DataSourceConfig;
import com.example.app.core.ParserCoverage;
import com.example.app.core.pep.Country;
import com.example.app.core.pep.PepCategory;
import com.example.app.core.pep.PepCategoryKind;
import com.example.app.core.pep.PepFunction;
import com.example.app.core.repository.CountryRepository;
import com.example.app.core.repository.ParserCoverageRepository;
import com.example.app.core.repository.PepCategoryRepository;
import com.example.app.core.repository.PepFunctionRepository;
import com.example.app.execution.ExecutionStatus;
import com.example.app.execution.SourceExecution;
import com.example.app.execution.SourceExecutionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Calcule la page « Couverture PEP » d'un pays.
 * Une fonction est couverte quand une source active et complète qui la déclare a déjà généré son fichier FTM.
 */
@Service
public class CoverageService {

    private final CountryRepository countryRepository;
    private final PepCategoryRepository categoryRepository;
    private final PepFunctionRepository functionRepository;
    private final ParserCoverageRepository coverageRepository;
    private final SourceExecutionRepository executionRepository;

    public CoverageService(CountryRepository countryRepository, PepCategoryRepository categoryRepository,
                           PepFunctionRepository functionRepository, ParserCoverageRepository coverageRepository,
                           SourceExecutionRepository executionRepository) {
        this.countryRepository = countryRepository;
        this.categoryRepository = categoryRepository;
        this.functionRepository = functionRepository;
        this.coverageRepository = coverageRepository;
        this.executionRepository = executionRepository;
    }

    @Transactional(readOnly = true)
    public CoverageDto coverage(String countryIso) {
        String iso = countryIso == null ? "" : countryIso.trim().toUpperCase();
        Country country = countryRepository.findById(iso).orElseThrow(() -> NotFoundException.of("Pays", iso));

        List<PepFunction> functions = functionRepository.findByCategoryCountryIso(iso);
        Map<Long, List<ParserCoverage>> coverageByFunction = new HashMap<>();
        for (ParserCoverage c : coverageRepository.findByPepFunctionCategoryCountryIso(iso)) {
            coverageByFunction.computeIfAbsent(c.getPepFunction().getId(), k -> new ArrayList<>()).add(c);
        }

        Map<Long, Optional<SourceExecution>> lastBySource = new HashMap<>();
        Map<Long, Optional<SourceExecution>> lastSuccessBySource = new HashMap<>();
        Set<Long> countedSources = new LinkedHashSet<>();
        int personCount = 0;

        List<CoverageDto.Category> categories = new ArrayList<>();
        int official = 0;
        int coveredOfficial = 0;
        for (PepCategory category : categoryRepository.findByCountryIsoOrderBySortOrderAsc(iso)) {
            List<CoverageDto.Function> functionDtos = new ArrayList<>();
            for (PepFunction function : functions) {
                if (!function.getCategory().getId().equals(category.getId())) {
                    continue;
                }
                List<CoverageDto.Source> sources = new ArrayList<>();
                for (ParserCoverage c : coverageByFunction.getOrDefault(function.getId(), List.of())) {
                    DataSourceConfig s = c.getDataSourceConfig();
                    if (s.getDeletedAt() != null) {
                        continue;
                    }
                    Optional<SourceExecution> last = lastBySource.computeIfAbsent(s.getId(),
                            id -> executionRepository.findFirstByDataSourceConfigIdOrderByStartedAtDesc(id));
                    Optional<SourceExecution> success = lastSuccessBySource.computeIfAbsent(s.getId(),
                            id -> executionRepository.findFirstByDataSourceConfigIdAndStatusOrderByStartedAtDesc(
                                    id, ExecutionStatus.SUCCESS));
                    if (success.isPresent() && s.isEnabled() && countedSources.add(s.getId())) {
                        personCount += Optional.ofNullable(success.get().getPersonCount()).orElse(0);
                    }
                    sources.add(new CoverageDto.Source(s.getId(), s.getSourceName(), s.getLabel(),
                            firstUrl(s.getExternalSourcePath()), c.getCompleteness(), c.isIncludesFormer(),
                            s.isEnabled(), SourceDto.isImplemented(s),
                            last.map(SourceExecution::getStatus).orElse(null),
                            success.map(SourceExecution::getFinishedAt).orElse(null),
                            success.map(SourceExecution::getPersonCount).orElse(null)));
                }
                functionDtos.add(new CoverageDto.Function(function.getId(), function.getLabel(),
                        function.getInstitution(), function.getExpectedHolders(), functionStatus(sources), sources));
            }
            CoverageDto.Status status = categoryStatus(functionDtos);
            if (category.getKind() == PepCategoryKind.OFFICIAL) {
                official++;
                if (status == CoverageDto.Status.COVERED) {
                    coveredOfficial++;
                }
            }
            categories.add(new CoverageDto.Category(category.getId(), category.getCode(), category.getLabel(),
                    category.getKind(), category.getRiskClass(), category.getLegalReference(), status, functionDtos));
        }
        return new CoverageDto(country, official, coveredOfficial, personCount, categories);
    }

    static CoverageDto.Status functionStatus(List<CoverageDto.Source> sources) {
        boolean partial = false;
        for (CoverageDto.Source s : sources) {
            if (!s.enabled() || s.lastSuccessAt() == null) {
                continue;
            }
            if (s.completeness() == Completeness.COMPLETE) {
                return CoverageDto.Status.COVERED;
            }
            partial = true;
        }
        return partial ? CoverageDto.Status.PARTIAL : CoverageDto.Status.NONE;
    }

    static CoverageDto.Status categoryStatus(List<CoverageDto.Function> functions) {
        if (functions.isEmpty()) {
            return CoverageDto.Status.NONE;
        }
        long covered = functions.stream().filter(f -> f.status() == CoverageDto.Status.COVERED).count();
        if (covered == functions.size()) {
            return CoverageDto.Status.COVERED;
        }
        boolean any = functions.stream().anyMatch(f -> f.status() != CoverageDto.Status.NONE);
        return any ? CoverageDto.Status.PARTIAL : CoverageDto.Status.NONE;
    }

    private static String firstUrl(String external) {
        if (external == null || external.isBlank()) {
            return null;
        }
        String first = external.trim().split("\\s+")[0];
        return first.startsWith("http") ? first : null;
    }
}
