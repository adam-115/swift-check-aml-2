package com.example.app.core.repository;

import com.example.app.core.Completeness;
import com.example.app.core.DataSourceConfig;
import com.example.app.core.ListType;
import com.example.app.core.ParserCoverage;
import com.example.app.core.ParserType;
import com.example.app.core.pep.Country;
import com.example.app.core.pep.PepCategory;
import com.example.app.core.pep.PepCategoryKind;
import com.example.app.core.pep.PepFunction;
import com.example.app.ingestion.model.SourceFormat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class PepReferenceRepositoryTest {

    @Autowired
    private CountryRepository countryRepository;

    @Autowired
    private PepCategoryRepository categoryRepository;

    @Autowired
    private PepFunctionRepository functionRepository;

    @Autowired
    private DataSourceConfigRepository dataSourceConfigRepository;

    @Autowired
    private ParserCoverageRepository coverageRepository;

    @Test
    @DisplayName("Référentiel PEP chargé par Liquibase : 27 pays UE + Suisse, catégories et fonctions du Luxembourg")
    void testReferenceDataLoaded() {
        assertThat(countryRepository.count()).isEqualTo(28);

        Country lu = countryRepository.findById("LU").orElseThrow();
        assertThat(lu.isEuMember()).isTrue();
        assertThat(lu.getRetentionMonths()).isEqualTo(12);
        assertThat(lu.getOfficialListReference()).isEqualTo("JO C/2023/724");
        assertThat(countryRepository.findById("CH").orElseThrow().isEuMember()).isFalse();

        List<PepCategory> luCategories = categoryRepository.findByCountryIsoOrderBySortOrderAsc("LU");
        assertThat(luCategories).hasSize(13);
        assertThat(luCategories.get(0).getCode()).isEqualTo("LU-a");
        assertThat(categoryRepository.findByCode("LU-MUN").orElseThrow().getKind())
                .isEqualTo(PepCategoryKind.RISK_BASED);

        assertThat(functionRepository.findByCategoryCountryIso("LU")).hasSize(26);
        PepFunction depute = functionRepository.findByCategoryCodeAndLabel("LU-b", "Député").orElseThrow();
        assertThat(depute.getInstitution()).isEqualTo("Chambre des Députés");
        assertThat(depute.getExpectedHolders()).isEqualTo(60);
    }

    @Test
    @DisplayName("Une source PEP déclare les fonctions qu'elle couvre")
    void testParserCoverage() {
        Country lu = countryRepository.findById("LU").orElseThrow();
        DataSourceConfig source = dataSourceConfigRepository.save(DataSourceConfig.builder()
                .sourceName("LU-CHAMBER-DEPUTIES")
                .sourceFormat(SourceFormat.CSV)
                .listType(ListType.PEP)
                .parserType(ParserType.OPEN_DATA_FILE)
                .country(lu)
                .retentionMonths(lu.getRetentionMonths())
                .externalSourcePath("https://data.public.lu/fr/datasets/r/a5b6e7f9-b488-41a6-9bd1-be913a5c35b5")
                .mappingFilePath("mappings/lu-chamber-deputies-mapping.json")
                .build());

        PepFunction depute = functionRepository.findByCategoryCodeAndLabel("LU-b", "Député").orElseThrow();
        coverageRepository.save(ParserCoverage.builder()
                .dataSourceConfig(source)
                .pepFunction(depute)
                .completeness(Completeness.COMPLETE)
                .includesFormer(false)
                .build());

        List<ParserCoverage> coverages = coverageRepository.findByDataSourceConfigId(source.getId());
        assertThat(coverages).hasSize(1);
        assertThat(coverages.get(0).getPepFunction().getCategory().getCode()).isEqualTo("LU-b");
        assertThat(coverageRepository.findByPepFunctionCategoryCountryIso("LU"))
                .extracting(c -> c.getDataSourceConfig().getSourceName())
                .contains("LU-CHAMBER-DEPUTIES", "lu_pep_chamber");

        DataSourceConfig found = dataSourceConfigRepository.findBySourceName("LU-CHAMBER-DEPUTIES").orElseThrow();
        assertThat(found.getListType()).isEqualTo(ListType.PEP);
        assertThat(found.getCountry().getIso()).isEqualTo("LU");
    }
}
