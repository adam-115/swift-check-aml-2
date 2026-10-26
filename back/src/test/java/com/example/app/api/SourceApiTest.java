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
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Gestion des sources : création, lecture, modification, activation, suppression, couverture PEP et erreurs.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SourceApiTest {

    @Autowired
    private MockMvc mvc;

    private long deputeId() throws Exception {
        String json = mvc.perform(get("/api/pep/functions").param("country", "LU"))
                .andReturn().getResponse().getContentAsString();
        List<Integer> ids = JsonPath.read(json, "$[?(@.label == 'Député')].id");
        return ids.get(0);
    }

    private String body(String name, String label) throws Exception {
        return """
                {
                  "sourceName": "%s",
                  "label": "%s",
                  "description": "Créée par le test",
                  "sourceFormat": "CSV",
                  "listType": "PEP",
                  "parserType": "OPEN_DATA_FILE",
                  "countryIso": "lu",
                  "retentionMonths": 12,
                  "externalSourcePath": "https://example.org/a.csv   https://example.org/b.csv",
                  "mappingFilePath": "mappings/test.json",
                  "coverage": [ { "functionId": %d, "completeness": "PARTIAL", "includesFormer": true } ]
                }
                """.formatted(name, label, deputeId());
    }

    private String create(String name) throws Exception {
        String json = mvc.perform(post("/api/sources").contentType(MediaType.APPLICATION_JSON)
                        .content(body(name, "Source " + name)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return String.valueOf((Integer) JsonPath.read(json, "$.id"));
    }

    @Test
    @DisplayName("Création : 201, valeurs normalisées, chemins de travail calculés, couverture enregistrée")
    void createSource() throws Exception {
        mvc.perform(post("/api/sources").contentType(MediaType.APPLICATION_JSON)
                        .content(body("test-src-create", "Source de test")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sourceName").value("test-src-create"))
                .andExpect(jsonPath("$.countryIso").value("LU"))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.externalSourcePath").value("https://example.org/a.csv\nhttps://example.org/b.csv"))
                .andExpect(jsonPath("$.localSourcePath").value("data/sources/test-src-create/source.csv"))
                .andExpect(jsonPath("$.ftmOutputPath").value(containsString("test-src-create.ftm.json")))
                .andExpect(jsonPath("$.coverage[0].label").value("Député"))
                .andExpect(jsonPath("$.coverage[0].completeness").value("PARTIAL"))
                .andExpect(jsonPath("$.coverage[0].includesFormer").value(true));
    }

    @Test
    @DisplayName("Fonction principale : renvoyée avec son libellé, ajoutée à la couverture, fonction inconnue (404)")
    void mainFunction() throws Exception {
        String json = mvc.perform(get("/api/pep/functions").param("country", "LU"))
                .andReturn().getResponse().getContentAsString();
        int former = JsonPath.<List<Integer>>read(json, "$[?(@.label == 'Ancien député')].id").get(0);

        String request = body("test-src-main", "Source principale")
                .replace("\"retentionMonths\": 12,", "\"retentionMonths\": 12, \"pepFunctionId\": " + former + ",");
        mvc.perform(post("/api/sources").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.pepFunctionId").value(former))
                .andExpect(jsonPath("$.pepFunctionLabel").value("Ancien député"))
                .andExpect(jsonPath("$.pepFunctionCategoryCode").value("LU-b"))
                .andExpect(jsonPath("$.coverage.length()").value(2))
                .andExpect(jsonPath("$.coverage[*].label").value(hasItem("Ancien député")));

        mvc.perform(post("/api/sources").contentType(MediaType.APPLICATION_JSON)
                        .content(request.replace("test-src-main", "test-src-main2")
                                .replace("\"pepFunctionId\": " + former, "\"pepFunctionId\": 999999")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Création refusée : nom déjà pris (409), champs invalides (400 par champ), pays inconnu (404)")
    void createErrors() throws Exception {
        create("test-src-dup");
        mvc.perform(post("/api/sources").contentType(MediaType.APPLICATION_JSON).content(body("test-src-dup", "X")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("existe déjà")));

        mvc.perform(post("/api/sources").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceName\": \"Mauvais Nom\", \"label\": \"\", \"externalSourcePath\": \"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.sourceName").exists())
                .andExpect(jsonPath("$.fields.label").value("obligatoire"))
                .andExpect(jsonPath("$.fields.sourceFormat").value("obligatoire"))
                .andExpect(jsonPath("$.fields.externalSourcePath").value("obligatoire"));

        mvc.perform(post("/api/sources").contentType(MediaType.APPLICATION_JSON)
                        .content(body("test-src-country", "X").replace("\"lu\"", "\"xx\"")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(containsString("Pays introuvable")));

        mvc.perform(post("/api/sources").contentType(MediaType.APPLICATION_JSON).content("{pas du json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Corps de requête JSON illisible"));
    }

    @Test
    @DisplayName("Modification, activation et renommage ; renommer vers un nom pris est refusé (409)")
    void updateSource() throws Exception {
        String id = create("test-src-update");
        create("test-src-other");

        mvc.perform(put("/api/sources/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content(body("test-src-renamed", "Libellé modifié")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceName").value("test-src-renamed"))
                .andExpect(jsonPath("$.label").value("Libellé modifié"));

        mvc.perform(patch("/api/sources/" + id + "/enabled").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\": false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));

        mvc.perform(put("/api/sources/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content(body("test-src-other", "X")))
                .andExpect(status().isConflict());
        mvc.perform(put("/api/sources/999999").contentType(MediaType.APPLICATION_JSON)
                        .content(body("test-src-x", "X")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Suppression : 204, la source et sa couverture disparaissent ; une seconde suppression donne 404")
    void deleteSource() throws Exception {
        String id = create("test-src-delete");
        mvc.perform(delete("/api/sources/" + id)).andExpect(status().isNoContent());
        mvc.perform(get("/api/sources/" + id)).andExpect(status().isNotFound());
        mvc.perform(get("/api/sources")).andExpect(jsonPath("$[*].sourceName", not(hasItem("test-src-delete"))));
        mvc.perform(delete("/api/sources/" + id)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Couverture PEP : la fonction déclarée par une source active partielle est « partielle »")
    void coverage() throws Exception {
        create("test-src-coverage");
        mvc.perform(get("/api/pep/coverage").param("country", "LU"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.officialCategories").value(9))
                .andExpect(jsonPath("$.totalFunctions").value(26))
                .andExpect(jsonPath("$.categories[?(@.code == 'LU-b')].functions[?(@.label == 'Député')].sources[*].sourceName",
                        hasItem("test-src-coverage")));
        mvc.perform(get("/api/pep/coverage").param("country", "XX"))
                .andExpect(status().isNotFound());
    }
}
