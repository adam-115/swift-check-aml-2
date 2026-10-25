package com.example.app.api;

import com.example.app.api.dto.CoverageDto;
import com.example.app.api.dto.PepFunctionDto;
import com.example.app.core.pep.Country;
import com.example.app.core.repository.CountryRepository;
import com.example.app.core.repository.PepFunctionRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;

/**
 * Référentiel PEP.
 * <pre>
 * GET /api/countries                pays couverts
 * GET /api/pep/coverage?country=LU  couverture PEP d'un pays
 * GET /api/pep/functions?country=LU fonctions PEP d'un pays (couverture d'une source)
 * </pre>
 */
@RestController
@RequestMapping("/api")
public class ReferenceController {

    private final CountryRepository countryRepository;
    private final CoverageService coverageService;
    private final PepFunctionRepository functionRepository;

    public ReferenceController(CountryRepository countryRepository, CoverageService coverageService,
                               PepFunctionRepository functionRepository) {
        this.countryRepository = countryRepository;
        this.coverageService = coverageService;
        this.functionRepository = functionRepository;
    }

    @GetMapping("/countries")
    public List<Country> countries() {
        return countryRepository.findAllByOrderByNameAsc();
    }

    @GetMapping("/pep/coverage")
    public CoverageDto coverage(@RequestParam(defaultValue = "LU") String country) {
        return coverageService.coverage(country);
    }

    /** Fonctions PEP d'un pays, dans l'ordre des catégories : choix de la couverture d'une source. */
    @GetMapping("/pep/functions")
    @Transactional(readOnly = true)
    public List<PepFunctionDto> functions(@RequestParam(defaultValue = "LU") String country) {
        return functionRepository.findByCategoryCountryIso(country.trim().toUpperCase()).stream()
                .map(PepFunctionDto::of)
                .sorted(Comparator.comparingInt(PepFunctionDto::categoryOrder).thenComparing(PepFunctionDto::id))
                .toList();
    }
}
