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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Gestion des sources depuis l'interface : création, modification, suppression, réinitialisation et erreurs.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SourceCrudApiTest {

    @Autowired
    private MockMvc mvc;

    private String body(String name, String extra) {
        return """
                {
                  "sourceName": "%s",
                  "label": "Source de test",
                  "description": "Créée par le test",
                  "sourceFormat": "CSV",
                  "listType": "PEP",
                  "parserType": "OPEN_DATA_FILE",
                  "countryIso": "lu",
                  "retentionMonths": 12,
                  "externalSourcePath": "https://example.org/a.csv   https://example.org/b.csv",
                  "mappingFilePath": "classpath:mappings/lu/lu_pep_chamber.mapping.json",
                  "converterOptions": "{ \\"encoding\\" : \\"windows-1252\\" }",
                  %s
                  "coverage": [ { "functionId": %d, "completeness": "PARTIAL", "includesFormer": true } ]
                }
                """.formatted(name, extra, functionId());
    }

    private long functionId() {
        try {
            String json = mvc.perform(get("/api/pep/functions").param("country", "LU"))
                    .andReturn().getResponse().getContentAsString();
            List<Integer> ids = JsonPath.read(json, "$[?(@.label == 'Député')].id");
            return ids.get(0);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private String create(String name) throws Exception {
        String json = mvc.perform(post("/api/sources").contentType(MediaType.APPLICATION_JSON).content(body(name, "")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return String.valueOf((Integer) JsonPath.read(json, "$.id"));
    }

    @Test
    @DisplayName("GET /api/pep/functions : les 26 fonctions du Luxembourg avec leur catégorie")
    void functions() throws Exception {
        mvc.perform(get("/api/pep/functions").param("country", "LU"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(26))
                .andExpect(jsonPath("$[0].categoryCode").value("LU-a"));
    }

    @Test
    @DisplayName("Création : 201, valeurs normalisées, chemins de travail calculés, couverture enregistrée")
    void createSource() throws Exception {
        mvc.perform(post("/api/sources").contentType(MediaType.APPLICATION_JSON).content(body("test_crud_create", "")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sourceName").value("test_crud_create"))
                .andExpect(jsonPath("$.countryIso").value("LU"))
                .andExpect(jsonPath("$.externalSourcePath").value("https://example.org/a.csv\nhttps://example.org/b.csv"))
                .andExpect(jsonPath("$.converterOptions").value("{\"encoding\":\"windows-1252\"}"))
                .andExpect(jsonPath("$.ftmOutputPath").value(containsString("test_crud_create.ftm.json")))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.implemented").value(true))
                .andExpect(jsonPath("$.inCatalog").value(false))
                .andExpect(jsonPath("$.customized").value(true))
                .andExpect(jsonPath("$.coverage[0].label").value("Député"))
                .andExpect(jsonPath("$.coverage[0].completeness").value("PARTIAL"));
    }

    @Test
    @DisplayName("Création refusée : nom déjà pris (409), champs invalides (400 par champ), JSON ou mapping invalides (400)")
    void createErrors() throws Exception {
        create("test_crud_dup");
        mvc.perform(post("/api/sources").contentType(MediaType.APPLICATION_JSON).content(body("test_crud_dup", "")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("existe déjà")));

        mvc.perform(post("/api/sources").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceName\": \"Mauvais Nom\", \"label\": \"\", \"externalSourcePath\": \"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.sourceName").exists())
                .andExpect(jsonPath("$.fields.label").value("obligatoire"))
                .andExpect(jsonPath("$.fields.sourceFormat").value("obligatoire"))
                .andExpect(jsonPath("$.fields.externalSourcePath").value("obligatoire"));

        String badJson = body("test_crud_badjson", "").replace("{ \\\"encoding\\\" : \\\"windows-1252\\\" }", "{ pas du json");
        mvc.perform(post("/api/sources").contentType(MediaType.APPLICATION_JSON).content(badJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("Options de conversion")));

        String badMapping = body("test_crud_badmapping", "")
                .replace("classpath:mappings/lu/lu_pep_chamber.mapping.json", "classpath:mappings/absent.json");
        mvc.perform(post("/api/sources").contentType(MediaType.APPLICATION_JSON).content(badMapping))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("Fichier de mapping introuvable")));

        String badCountry = body("test_crud_badcountry", "").replace("\"lu\"", "\"xx\"");
        mvc.perform(post("/api/sources").contentType(MediaType.APPLICATION_JSON).content(badCountry))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(containsString("Pays introuvable")));
    }

    @Test
    @DisplayName("Modification : 200 ; renommer est refusé (400)")
    void updateSource() throws Exception {
        String id = create("test_crud_update");
        mvc.perform(put("/api/sources/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content(body("test_crud_update", "\"enabled\": false,").replace("Source de test", "Libellé modifié")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.label").value("Libellé modifié"))
                .andExpect(jsonPath("$.enabled").value(false));

        mvc.perform(put("/api/sources/" + id).contentType(MediaType.APPLICATION_JSON).content(body("test_crud_autre", "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("ne change pas")));
        mvc.perform(put("/api/sources/999999").contentType(MediaType.APPLICATION_JSON).content(body("test_crud_update", "")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Suppression : 204, la source disparaît de la liste et ne s'exécute plus ; la recréer la fait revenir")
    void deleteSource() throws Exception {
        String id = create("test_crud_delete");
        mvc.perform(delete("/api/sources/" + id)).andExpect(status().isNoContent());

        mvc.perform(get("/api/sources/" + id)).andExpect(status().isNotFound());
        mvc.perform(get("/api/sources"))
                .andExpect(jsonPath("$[*].sourceName", not(hasItem("test_crud_delete"))));
        mvc.perform(post("/api/sources/" + id + "/executions")).andExpect(status().isNotFound());
        mvc.perform(delete("/api/sources/" + id)).andExpect(status().isNotFound());

        mvc.perform(post("/api/sources").contentType(MediaType.APPLICATION_JSON).content(body("test_crud_delete", "")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(Integer.parseInt(id)));
    }

    @Test
    @DisplayName("Réinitialisation : une source du catalogue revient à sa définition ; une source hors catalogue est refusée (400)")
    void resetSource() throws Exception {
        String json = mvc.perform(get("/api/sources")).andReturn().getResponse().getContentAsString();
        List<Integer> ids = JsonPath.read(json, "$[?(@.sourceName == 'lu_pep_chamber')].id");
        String chamber = String.valueOf(ids.get(0));

        mvc.perform(put("/api/sources/" + chamber).contentType(MediaType.APPLICATION_JSON)
                        .content(body("lu_pep_chamber", "")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customized").value(true))
                .andExpect(jsonPath("$.inCatalog").value(true));
        mvc.perform(post("/api/sources/" + chamber + "/reset"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customized").value(false))
                .andExpect(jsonPath("$.label").value("Députés en fonction (Luxembourg)"))
                .andExpect(jsonPath("$.coverage[0].completeness").value("COMPLETE"));

        String own = create("test_crud_reset");
        mvc.perform(post("/api/sources/" + own + "/reset"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("catalogue")));
    }
}
