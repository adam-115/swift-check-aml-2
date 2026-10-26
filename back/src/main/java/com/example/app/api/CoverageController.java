package com.example.app.api;

import com.example.app.core.Completeness;
import com.example.app.core.entities.Country;
import com.example.app.core.entities.DataSourceConfig;
import com.example.app.core.entities.ParserCoverage;
import com.example.app.core.entities.PepCategory;
import com.example.app.core.entities.PepFunction;
import com.example.app.core.pep.PepCategoryKind;
import com.example.app.core.repository.ParserCoverageRepository;
import com.example.app.core.repository.PepCategoryRepository;
import com.example.app.core.repository.PepFunctionRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Couverture PEP déclarée : pour chaque catégorie et fonction d'un pays, les sources qui la couvrent.
 * <pre>
 * GET /api/pep/coverage?country=LU
 * </pre>
 * Une fonction est couverte quand une source active la déclare complète, partielle quand seules des sources
 * actives partielles la déclarent.
 */
@RestController
@RequestMapping("/api/pep/coverage")
public class CoverageController {

    public enum Status { COVERED, PARTIAL, NONE }

    public record SourceRef(Long id, String sourceName, String label, Completeness completeness,
                            boolean includesFormer, boolean enabled) {
    }

    public record FunctionCoverage(Long id, String label, String institution, Integer expectedHolders,
                                   Status status, List<SourceRef> sources) {
    }

    public record CategoryCoverage(Long id, String code, String label, PepCategoryKind kind, int riskClass,
                                   String legalReference, Status status, List<FunctionCoverage> functions) {
    }

    public record Coverage(Country country, int officialCategories, int coveredOfficialCategories,
                           int coveredFunctions, int totalFunctions, List<CategoryCoverage> categories) {
    }

    private final ReferenceService referenceService;
    private final PepCategoryRepository categoryRepository;
    private final PepFunctionRepository functionRepository;
    private final ParserCoverageRepository coverageRepository;

    public CoverageController(ReferenceService referenceService, PepCategoryRepository categoryRepository,
                              PepFunctionRepository functionRepository, ParserCoverageRepository coverageRepository) {
        this.referenceService = referenceService;
        this.categoryRepository = categoryRepository;
        this.functionRepository = functionRepository;
        this.coverageRepository = coverageRepository;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public Coverage coverage(@RequestParam(defaultValue = "LU") String country) {
        Country c = referenceService.country(country);
        Map<Long, List<SourceRef>> sourcesByFunction = new HashMap<>();
        for (ParserCoverage pc : coverageRepository.findByPepFunctionCategoryCountryIso(c.getIso())) {
            DataSourceConfig s = pc.getDataSourceConfig();
            sourcesByFunction.computeIfAbsent(pc.getPepFunction().getId(), k -> new ArrayList<>())
                    .add(new SourceRef(s.getId(), s.getSourceName(), s.getLabel(), pc.getCompleteness(),
                            pc.isIncludesFormer(), s.isEnabled()));
        }

        List<PepFunction> functions = functionRepository.findByCategoryCountryIso(c.getIso());
        List<CategoryCoverage> categories = new ArrayList<>();
        int official = 0;
        int coveredOfficial = 0;
        int covered = 0;
        for (PepCategory category : categoryRepository.findByCountryIsoOrderBySortOrderAsc(c.getIso())) {
            List<FunctionCoverage> items = new ArrayList<>();
            for (PepFunction f : functions) {
                if (f.getCategory().getId().equals(category.getId())) {
                    List<SourceRef> sources = sourcesByFunction.getOrDefault(f.getId(), List.of());
                    Status status = functionStatus(sources);
                    if (status == Status.COVERED) {
                        covered++;
                    }
                    items.add(new FunctionCoverage(f.getId(), f.getLabel(), f.getInstitution(),
                            f.getExpectedHolders(), status, sources));
                }
            }
            Status status = categoryStatus(items);
            if (category.getKind() == PepCategoryKind.OFFICIAL) {
                official++;
                if (status == Status.COVERED) {
                    coveredOfficial++;
                }
            }
            categories.add(new CategoryCoverage(category.getId(), category.getCode(), category.getLabel(),
                    category.getKind(), category.getRiskClass(), category.getLegalReference(), status, items));
        }
        return new Coverage(c, official, coveredOfficial, covered, functions.size(), categories);
    }

    static Status functionStatus(List<SourceRef> sources) {
        boolean partial = false;
        for (SourceRef s : sources) {
            if (!s.enabled()) {
                continue;
            }
            if (s.completeness() == Completeness.COMPLETE) {
                return Status.COVERED;
            }
            partial = true;
        }
        return partial ? Status.PARTIAL : Status.NONE;
    }

    static Status categoryStatus(List<FunctionCoverage> functions) {
        if (functions.isEmpty()) {
            return Status.NONE;
        }
        if (functions.stream().allMatch(f -> f.status() == Status.COVERED)) {
            return Status.COVERED;
        }
        return functions.stream().anyMatch(f -> f.status() != Status.NONE) ? Status.PARTIAL : Status.NONE;
    }
}
