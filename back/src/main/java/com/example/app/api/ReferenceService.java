package com.example.app.api;

import com.example.app.api.dto.ReferenceDtos.CategoryRequest;
import com.example.app.api.dto.ReferenceDtos.CountryRequest;
import com.example.app.api.dto.ReferenceDtos.FunctionRequest;
import com.example.app.core.entities.Country;
import com.example.app.core.entities.PepCategory;
import com.example.app.core.entities.PepFunction;
import com.example.app.core.repository.CountryRepository;
import com.example.app.core.repository.DataSourceConfigRepository;
import com.example.app.core.repository.ParserCoverageRepository;
import com.example.app.core.repository.PepCategoryRepository;
import com.example.app.core.repository.PepFunctionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gestion du référentiel PEP. Un élément encore utilisé ne peut pas être supprimé : pays avec des
 * catégories ou des sources, catégorie avec des fonctions, fonction couverte par une source.
 */
@Service
public class ReferenceService {

    private final CountryRepository countryRepository;
    private final PepCategoryRepository categoryRepository;
    private final PepFunctionRepository functionRepository;
    private final ParserCoverageRepository coverageRepository;
    private final DataSourceConfigRepository sourceRepository;

    public ReferenceService(CountryRepository countryRepository, PepCategoryRepository categoryRepository,
                            PepFunctionRepository functionRepository, ParserCoverageRepository coverageRepository,
                            DataSourceConfigRepository sourceRepository) {
        this.countryRepository = countryRepository;
        this.categoryRepository = categoryRepository;
        this.functionRepository = functionRepository;
        this.coverageRepository = coverageRepository;
        this.sourceRepository = sourceRepository;
    }

    // ------------------------------------------------------------------ Pays

    public Country country(String iso) {
        String code = iso == null ? "" : iso.trim().toUpperCase();
        return countryRepository.findById(code).orElseThrow(() -> NotFoundException.of("Pays", code));
    }

    @Transactional
    public Country createCountry(CountryRequest r) {
        String iso = r.iso().trim().toUpperCase();
        if (countryRepository.existsById(iso)) {
            throw new ConflictException("Le pays " + iso + " existe déjà.");
        }
        Country country = new Country();
        country.setIso(iso);
        return saveCountry(country, r);
    }

    @Transactional
    public Country updateCountry(String iso, CountryRequest r) {
        Country country = country(iso);
        if (!country.getIso().equalsIgnoreCase(r.iso().trim())) {
            throw new IllegalArgumentException("Le code ISO d'un pays ne change pas.");
        }
        return saveCountry(country, r);
    }

    @Transactional
    public void deleteCountry(String iso) {
        Country country = country(iso);
        if (categoryRepository.existsByCountryIso(country.getIso())) {
            throw new ConflictException("Le pays " + country.getName()
                    + " a des catégories PEP : supprimez-les d'abord.");
        }
        if (sourceRepository.existsByCountryIso(country.getIso())) {
            throw new ConflictException("Des sources sont rattachées au pays " + country.getName()
                    + " : modifiez-les d'abord.");
        }
        countryRepository.delete(country);
    }

    private Country saveCountry(Country c, CountryRequest r) {
        c.setName(r.name().trim());
        c.setEuMember(Boolean.TRUE.equals(r.euMember()));
        c.setLegalBasis(blankToNull(r.legalBasis()));
        c.setRetentionMonths(r.retentionMonths());
        c.setOfficialListReference(blankToNull(r.officialListReference()));
        c.setOfficialListDate(r.officialListDate());
        c.setOfficialListUrl(blankToNull(r.officialListUrl()));
        return countryRepository.save(c);
    }

    // ------------------------------------------------------------ Catégories

    public PepCategory category(Long id) {
        return categoryRepository.findById(id).orElseThrow(() -> NotFoundException.of("Catégorie PEP", id));
    }

    @Transactional
    public PepCategory createCategory(CategoryRequest r) {
        checkCodeFree(r.code(), null);
        return saveCategory(new PepCategory(), r);
    }

    @Transactional
    public PepCategory updateCategory(Long id, CategoryRequest r) {
        PepCategory category = category(id);
        checkCodeFree(r.code(), id);
        return saveCategory(category, r);
    }

    @Transactional
    public void deleteCategory(Long id) {
        PepCategory category = category(id);
        long functions = functionRepository.countByCategoryId(id);
        if (functions > 0) {
            throw new ConflictException("La catégorie " + category.getCode() + " contient " + functions
                    + " fonction(s) : supprimez-les d'abord.");
        }
        categoryRepository.delete(category);
    }

    private void checkCodeFree(String code, Long currentId) {
        categoryRepository.findByCode(code.trim()).ifPresent(other -> {
            if (!other.getId().equals(currentId)) {
                throw new ConflictException("Une catégorie de code « " + code.trim() + " » existe déjà.");
            }
        });
    }

    private PepCategory saveCategory(PepCategory c, CategoryRequest r) {
        c.setCountry(country(r.countryIso()));
        c.setCode(r.code().trim());
        c.setLabel(r.label().trim());
        c.setKind(r.kind());
        c.setRiskClass(r.riskClass());
        c.setSortOrder(r.sortOrder() == null ? 0 : r.sortOrder());
        c.setLegalReference(blankToNull(r.legalReference()));
        return categoryRepository.save(c);
    }

    // ------------------------------------------------------------- Fonctions

    public PepFunction function(Long id) {
        return functionRepository.findById(id).orElseThrow(() -> NotFoundException.of("Fonction PEP", id));
    }

    @Transactional
    public PepFunction createFunction(FunctionRequest r) {
        return saveFunction(new PepFunction(), r);
    }

    @Transactional
    public PepFunction updateFunction(Long id, FunctionRequest r) {
        return saveFunction(function(id), r);
    }

    @Transactional
    public void deleteFunction(Long id) {
        PepFunction function = function(id);
        long mainOf = sourceRepository.countByPepFunctionId(id);
        if (mainOf > 0) {
            throw new ConflictException("La fonction « " + function.getLabel() + " » est la fonction principale de "
                    + mainOf + " source(s) : changez-la d'abord.");
        }
        long sources = coverageRepository.countByPepFunctionId(id);
        if (sources > 0) {
            throw new ConflictException("La fonction « " + function.getLabel() + " » est couverte par " + sources
                    + " source(s) : retirez-la de leur couverture d'abord.");
        }
        functionRepository.delete(function);
    }

    private PepFunction saveFunction(PepFunction f, FunctionRequest r) {
        f.setCategory(category(r.categoryId()));
        f.setLabel(r.label().trim());
        f.setInstitution(blankToNull(r.institution()));
        f.setExpectedHolders(r.expectedHolders());
        return functionRepository.save(f);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
