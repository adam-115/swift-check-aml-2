package com.example.app.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * API REST : référentiel, sources du catalogue, couverture PEP et réponses d'erreur.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ApiControllerTest {

    @Autowired
    private MockMvc mvc;

    @Test
    @DisplayName("GET /api/countries : 28 pays")
    void countries() throws Exception {
        mvc.perform(get("/api/countries"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(28))
                .andExpect(jsonPath("$[*].iso", hasItem("LU")));
    }

    @Test
    @DisplayName("GET /api/sources?listType=PEP&country=LU : les sources du catalogue avec leur couverture")
    void luPepSources() throws Exception {
        mvc.perform(get("/api/sources").param("listType", "PEP").param("country", "LU"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(greaterThanOrEqualTo(24)))
                .andExpect(jsonPath("$[?(@.sourceName == 'lu_pep_chamber')].implemented").value(hasItem(true)))
                .andExpect(jsonPath("$[?(@.sourceName == 'lu_pep_legilux')].implemented").value(hasItem(false)))
                .andExpect(jsonPath("$[?(@.sourceName == 'lu_pep_chamber')].coverage[0].label").value(hasItem("Député")));
    }

    @Test
    @DisplayName("GET /api/pep/coverage?country=LU : 13 catégories, 9 catégories officielles")
    void coverage() throws Exception {
        mvc.perform(get("/api/pep/coverage").param("country", "LU"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.country.iso").value("LU"))
                .andExpect(jsonPath("$.officialCategories").value(9))
                .andExpect(jsonPath("$.categories.length()").value(13))
                .andExpect(jsonPath("$.categories[1].functions[0].sources[0].sourceName").exists());
    }

    @Test
    @DisplayName("Erreurs : 404 source / pays / exécution inconnus, 409 source sans mapping, 400 paramètre invalide")
    void errors() throws Exception {
        mvc.perform(get("/api/sources/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Source introuvable : 999999"));
        mvc.perform(get("/api/pep/coverage").param("country", "XX"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(containsString("Pays introuvable")));
        mvc.perform(get("/api/executions/999999"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/sources").param("listType", "INCONNU"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        String legiluxId = idOf("lu_pep_legilux");
        mvc.perform(post("/api/sources/" + legiluxId + "/executions"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("fichier de mapping")));
    }

    @Test
    @DisplayName("PATCH /api/sources/{id} : désactive la source ; une adresse vide est refusée (400)")
    void updateSource() throws Exception {
        String id = idOf("lu_pep_chamber");
        mvc.perform(patch("/api/sources/" + id).contentType(MediaType.APPLICATION_JSON).content("{\"enabled\": false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));
        mvc.perform(post("/api/sources/" + id + "/executions"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("désactivée")));
        mvc.perform(patch("/api/sources/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"externalSourcePath\": \"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("ne peut pas être vide")));
        mvc.perform(patch("/api/sources/" + id).contentType(MediaType.APPLICATION_JSON).content("{pas du json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Corps de requête JSON illisible"));
    }

    @Test
    @DisplayName("Adresse d'API inconnue : 404 au format ApiError")
    void unknownApiPath() throws Exception {
        mvc.perform(get("/api/inconnu"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    private String idOf(String sourceName) throws Exception {
        String body = mvc.perform(get("/api/sources")).andReturn().getResponse().getContentAsString();
        java.util.List<Integer> ids = com.jayway.jsonpath.JsonPath.read(body,
                "$[?(@.sourceName == '" + sourceName + "')].id");
        return String.valueOf(ids.get(0));
    }
}
