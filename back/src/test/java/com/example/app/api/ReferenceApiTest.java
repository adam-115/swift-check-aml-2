package com.example.app.api;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Référentiel PEP : données chargées par Liquibase, gestion des pays, catégories et fonctions, règles de
 * suppression.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ReferenceApiTest {

    @Autowired
    private MockMvc mvc;

    private static final String COUNTRY = """
            { "iso": "zz", "name": "Pays de test", "euMember": false, "legalBasis": "Loi test", "retentionMonths": 18 }
            """;

    private String id(String json) {
        return String.valueOf((Integer) JsonPath.read(json, "$.id"));
    }

    @Test
    @DisplayName("Données de départ : 28 pays, 13 catégories et 26 fonctions pour le Luxembourg")
    void referenceData() throws Exception {
        mvc.perform(get("/api/countries"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(28))
                .andExpect(jsonPath("$[*].iso", hasItem("LU")));
        mvc.perform(get("/api/countries/lu"))
                .andExpect(jsonPath("$.officialListReference").value("JO C/2023/724"));
        mvc.perform(get("/api/pep/categories").param("country", "LU"))
                .andExpect(jsonPath("$.length()").value(13))
                .andExpect(jsonPath("$[0].code").value("LU-a"))
                .andExpect(jsonPath("$[0].functionCount").value(3));
        mvc.perform(get("/api/pep/functions").param("country", "LU"))
                .andExpect(jsonPath("$.length()").value(26))
                .andExpect(jsonPath("$[0].categoryCode").value("LU-a"));
    }

    @Test
    @DisplayName("Pays : création (201), doublon (409), modification, code ISO non modifiable (400), suppression (204)")
    void countryCrud() throws Exception {
        mvc.perform(post("/api/countries").contentType(MediaType.APPLICATION_JSON).content(COUNTRY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.iso").value("ZZ"))
                .andExpect(jsonPath("$.retentionMonths").value(18));
        mvc.perform(post("/api/countries").contentType(MediaType.APPLICATION_JSON).content(COUNTRY))
                .andExpect(status().isConflict());
        mvc.perform(put("/api/countries/ZZ").contentType(MediaType.APPLICATION_JSON)
                        .content(COUNTRY.replace("Pays de test", "Pays modifié")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Pays modifié"));
        mvc.perform(put("/api/countries/ZZ").contentType(MediaType.APPLICATION_JSON)
                        .content(COUNTRY.replace("\"zz\"", "\"zy\"")))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/countries").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"iso\": \"zzz\", \"name\": \"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.iso").exists())
                .andExpect(jsonPath("$.fields.name").value("obligatoire"))
                .andExpect(jsonPath("$.fields.retentionMonths").value("obligatoire"));
        mvc.perform(delete("/api/countries/ZZ")).andExpect(status().isNoContent());
        mvc.perform(get("/api/countries/ZZ")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Suppressions refusées (409) : pays avec catégories, catégorie avec fonctions, fonction couverte")
    void deletionRules() throws Exception {
        mvc.perform(delete("/api/countries/LU"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("catégories PEP")));

        String categories = mvc.perform(get("/api/pep/categories").param("country", "LU"))
                .andReturn().getResponse().getContentAsString();
        List<Integer> luA = JsonPath.read(categories, "$[?(@.code == 'LU-a')].id");
        mvc.perform(delete("/api/pep/categories/" + luA.get(0)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("fonction(s)")));

        String functions = mvc.perform(get("/api/pep/functions").param("country", "LU"))
                .andReturn().getResponse().getContentAsString();
        Integer depute = JsonPath.<List<Integer>>read(functions, "$[?(@.label == 'Député')].id").get(0);
        mvc.perform(post("/api/sources").contentType(MediaType.APPLICATION_JSON).content("""
                        { "sourceName": "test-ref-source", "label": "Test", "sourceFormat": "CSV",
                          "externalSourcePath": "https://example.org/a.csv",
                          "coverage": [ { "functionId": %d, "completeness": "COMPLETE" } ] }
                        """.formatted(depute)))
                .andExpect(status().isCreated());
        mvc.perform(delete("/api/pep/functions/" + depute))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("source(s)")));
    }

    @Test
    @DisplayName("Catégorie et fonction : création, code unique (409), modification, suppression")
    void categoryAndFunctionCrud() throws Exception {
        String category = mvc.perform(post("/api/pep/categories").contentType(MediaType.APPLICATION_JSON).content("""
                        { "countryIso": "LU", "code": "LU-TEST", "label": "Catégorie de test", "kind": "RISK_BASED",
                          "riskClass": 3, "sortOrder": 99, "legalReference": "Test" }
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.functionCount").value(0))
                .andReturn().getResponse().getContentAsString();
        String categoryId = id(category);

        mvc.perform(post("/api/pep/categories").contentType(MediaType.APPLICATION_JSON).content("""
                        { "countryIso": "LU", "code": "LU-TEST", "label": "Doublon", "kind": "OFFICIAL", "riskClass": 1 }
                        """))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/pep/categories").contentType(MediaType.APPLICATION_JSON).content("""
                        { "countryIso": "LU", "code": "LU-X", "label": "Classe invalide", "kind": "OFFICIAL", "riskClass": 7 }
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.riskClass").value("entre 1 et 4"));

        String function = mvc.perform(post("/api/pep/functions").contentType(MediaType.APPLICATION_JSON).content("""
                        { "categoryId": %s, "label": "Fonction de test", "institution": "Institution", "expectedHolders": 5 }
                        """.formatted(categoryId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.categoryCode").value("LU-TEST"))
                .andReturn().getResponse().getContentAsString();
        String functionId = id(function);

        mvc.perform(put("/api/pep/functions/" + functionId).contentType(MediaType.APPLICATION_JSON).content("""
                        { "categoryId": %s, "label": "Fonction renommée", "expectedHolders": 6 }
                        """.formatted(categoryId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.label").value("Fonction renommée"))
                .andExpect(jsonPath("$.institution").doesNotExist());
        mvc.perform(get("/api/pep/categories/" + categoryId)).andExpect(jsonPath("$.functionCount").value(1));

        mvc.perform(delete("/api/pep/functions/" + functionId)).andExpect(status().isNoContent());
        mvc.perform(delete("/api/pep/categories/" + categoryId)).andExpect(status().isNoContent());
        mvc.perform(get("/api/pep/categories/" + categoryId)).andExpect(status().isNotFound());
    }
}
