package com.example.app.api;

import com.example.app.api.dto.ReferenceDtos.CategoryDto;
import com.example.app.api.dto.ReferenceDtos.CategoryRequest;
import com.example.app.api.dto.ReferenceDtos.CountryRequest;
import com.example.app.api.dto.ReferenceDtos.FunctionDto;
import com.example.app.api.dto.ReferenceDtos.FunctionRequest;
import com.example.app.core.entities.Country;
import com.example.app.core.entities.PepCategory;
import com.example.app.core.entities.PepFunction;
import com.example.app.core.repository.CountryRepository;
import com.example.app.core.repository.ParserCoverageRepository;
import com.example.app.core.repository.PepCategoryRepository;
import com.example.app.core.repository.PepFunctionRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;

/**
 * Référentiel PEP.
 * <pre>
 * GET|POST          /api/countries                     pays
 * GET|PUT|DELETE    /api/countries/{iso}
 * GET|POST          /api/pep/categories?country=LU     catégories d'un pays
 * GET|PUT|DELETE    /api/pep/categories/{id}
 * GET|POST          /api/pep/functions?country=LU      fonctions d'un pays (ou ?categoryId=)
 * GET|PUT|DELETE    /api/pep/functions/{id}
 * </pre>
 */
@RestController
@RequestMapping("/api")
public class ReferenceController {

    private final CountryRepository countryRepository;
    private final PepCategoryRepository categoryRepository;
    private final PepFunctionRepository functionRepository;
    private final ParserCoverageRepository coverageRepository;
    private final ReferenceService referenceService;

    public ReferenceController(CountryRepository countryRepository, PepCategoryRepository categoryRepository,
                               PepFunctionRepository functionRepository, ParserCoverageRepository coverageRepository,
                               ReferenceService referenceService) {
        this.countryRepository = countryRepository;
        this.categoryRepository = categoryRepository;
        this.functionRepository = functionRepository;
        this.coverageRepository = coverageRepository;
        this.referenceService = referenceService;
    }

    // ------------------------------------------------------------------ Pays

    @GetMapping("/countries")
    public List<Country> countries() {
        return countryRepository.findAllByOrderByNameAsc();
    }

    @GetMapping("/countries/{iso}")
    public Country country(@PathVariable String iso) {
        return referenceService.country(iso);
    }

    @PostMapping("/countries")
    @ResponseStatus(HttpStatus.CREATED)
    public Country createCountry(@Valid @RequestBody CountryRequest request) {
        return referenceService.createCountry(request);
    }

    @PutMapping("/countries/{iso}")
    public Country updateCountry(@PathVariable String iso, @Valid @RequestBody CountryRequest request) {
        return referenceService.updateCountry(iso, request);
    }

    @DeleteMapping("/countries/{iso}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCountry(@PathVariable String iso) {
        referenceService.deleteCountry(iso);
    }

    // ------------------------------------------------------------ Catégories

    @GetMapping("/pep/categories")
    @Transactional(readOnly = true)
    public List<CategoryDto> categories(@RequestParam(defaultValue = "LU") String country) {
        return categoryRepository.findByCountryIsoOrderBySortOrderAsc(country.trim().toUpperCase()).stream()
                .map(this::toDto)
                .toList();
    }

    @GetMapping("/pep/categories/{id}")
    @Transactional(readOnly = true)
    public CategoryDto category(@PathVariable Long id) {
        return toDto(referenceService.category(id));
    }

    @PostMapping("/pep/categories")
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryDto createCategory(@Valid @RequestBody CategoryRequest request) {
        return toDto(referenceService.createCategory(request));
    }

    @PutMapping("/pep/categories/{id}")
    public CategoryDto updateCategory(@PathVariable Long id, @Valid @RequestBody CategoryRequest request) {
        return toDto(referenceService.updateCategory(id, request));
    }

    @DeleteMapping("/pep/categories/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCategory(@PathVariable Long id) {
        referenceService.deleteCategory(id);
    }

    // ------------------------------------------------------------- Fonctions

    @GetMapping("/pep/functions")
    @Transactional(readOnly = true)
    public List<FunctionDto> functions(@RequestParam(defaultValue = "LU") String country,
                                       @RequestParam(required = false) Long categoryId) {
        List<PepFunction> functions = categoryId != null
                ? functionRepository.findByCategoryId(categoryId)
                : functionRepository.findByCategoryCountryIso(country.trim().toUpperCase());
        return functions.stream()
                .map(this::toDto)
                .sorted(Comparator.comparingInt(FunctionDto::categoryOrder).thenComparing(FunctionDto::id))
                .toList();
    }

    @GetMapping("/pep/functions/{id}")
    @Transactional(readOnly = true)
    public FunctionDto function(@PathVariable Long id) {
        return toDto(referenceService.function(id));
    }

    @PostMapping("/pep/functions")
    @ResponseStatus(HttpStatus.CREATED)
    public FunctionDto createFunction(@Valid @RequestBody FunctionRequest request) {
        return toDto(referenceService.createFunction(request));
    }

    @PutMapping("/pep/functions/{id}")
    public FunctionDto updateFunction(@PathVariable Long id, @Valid @RequestBody FunctionRequest request) {
        return toDto(referenceService.updateFunction(id, request));
    }

    @DeleteMapping("/pep/functions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteFunction(@PathVariable Long id) {
        referenceService.deleteFunction(id);
    }

    private CategoryDto toDto(PepCategory c) {
        return CategoryDto.of(c, functionRepository.countByCategoryId(c.getId()));
    }

    private FunctionDto toDto(PepFunction f) {
        return FunctionDto.of(f, coverageRepository.countByPepFunctionId(f.getId()));
    }
}
