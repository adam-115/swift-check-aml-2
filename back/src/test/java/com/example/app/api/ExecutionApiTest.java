package com.example.app.api;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exécution d'une source par l'API : lancement en arrière-plan, historique, fichiers archivés téléchargeables.
 * Pas de transaction de test : l'exécution tourne dans un autre thread et doit voir la source enregistrée.
 */
@SpringBootTest(properties = "app.data-dir=target/test-data")
@AutoConfigureMockMvc
class ExecutionApiTest {

    @Autowired
    private MockMvc mvc;

    private String createSource(String name, Path dir, String mappingPath) throws Exception {
        // Source restée d'un test interrompu
        String existing = mvc.perform(get("/api/sources")).andReturn().getResponse().getContentAsString();
        for (Integer id : JsonPath.<java.util.List<Integer>>read(existing, "$[?(@.sourceName == '" + name + "')].id")) {
            mvc.perform(delete("/api/sources/" + id));
        }
        Path csv = dir.resolve("in.csv");
        Files.writeString(csv, "NAME,FIRSTNAME\nDoe,John\nDoe,John\n");
        String json = mvc.perform(post("/api/sources").contentType(MediaType.APPLICATION_JSON).content("""
                        { "sourceName": "%s", "label": "Test exécution", "sourceFormat": "CSV",
                          "externalSourcePath": "%s",
                          "localSourcePath": "%s", "initialJsonSourcePath": "%s",
                          "mappingFilePath": "%s", "ftmOutputPath": "%s" }
                        """.formatted(name, slash(csv), slash(dir.resolve("work/source.csv")),
                        slash(dir.resolve("work/records.json")), mappingPath, slash(dir.resolve("work/out.ftm.json")))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return String.valueOf((Integer) JsonPath.read(json, "$.id"));
    }

    private String runAndWait(String sourceId) throws Exception {
        String started = mvc.perform(post("/api/sources/" + sourceId + "/executions"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andReturn().getResponse().getContentAsString();
        String executionId = String.valueOf((Integer) JsonPath.read(started, "$.id"));
        for (int i = 0; i < 100; i++) {
            String json = mvc.perform(get("/api/executions/" + executionId)).andReturn().getResponse().getContentAsString();
            if (!"RUNNING".equals(JsonPath.read(json, "$.status"))) {
                return executionId;
            }
            Thread.sleep(200);
        }
        throw new AssertionError("Exécution " + executionId + " toujours en cours");
    }

    private static String slash(Path p) {
        return p.toAbsolutePath().toString().replace('\\', '/');
    }

    @Test
    @DisplayName("Succès : FTM généré, historique, 4 fichiers téléchargeables, suppression avec la source")
    void successfulExecution(@TempDir Path dir) throws Exception {
        Path mapping = dir.resolve("mapping.json");
        Files.writeString(mapping, """
                { "rules": [ { "ftmSchema": "Person", "idJsonPaths": ["$.NAME"],
                               "propertiesMapping": { "name": "{$.FIRSTNAME} {$.NAME}" } } ] }
                """);
        String sourceId = createSource("test-exec-ok", dir, slash(mapping));
        String executionId = runAndWait(sourceId);

        mvc.perform(get("/api/executions/" + executionId))
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.entityCount").value(2))
                .andExpect(jsonPath("$.schemaCounts").value("Person: 2"))
                .andExpect(jsonPath("$.errorMessage").value(nullValue()))
                .andExpect(jsonPath("$.files[0].size").isNumber())
                .andExpect(jsonPath("$.files[1].size").isNumber())
                .andExpect(jsonPath("$.files[2].size").isNumber())
                .andExpect(jsonPath("$.files[3].size").isNumber());

        mvc.perform(get("/api/executions/" + executionId + "/files/ftm"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("test-exec-ok_" + executionId)))
                .andExpect(content().string(containsString("John Doe")));
        mvc.perform(get("/api/executions/" + executionId + "/files/source"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Doe,John")));
        mvc.perform(get("/api/executions/" + executionId + "/files/autre")).andExpect(status().isBadRequest());

        // Deux lignes FTM de même identifiant : une seule entité, comme le veut FTM
        mvc.perform(get("/api/executions/" + executionId + "/entities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.schemas.Person").value(1))
                .andExpect(jsonPath("$.items[0].caption").value("John Doe"))
                .andExpect(jsonPath("$.items[0].properties.name.length()").value(1));
        mvc.perform(get("/api/executions/" + executionId + "/entities").param("q", "inconnu"))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.matching").value(0));

        mvc.perform(get("/api/sources/" + sourceId + "/executions"))
                .andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/executions").param("sourceId", sourceId))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].sourceLabel").value("Test exécution"));
        mvc.perform(get("/api/sources/" + sourceId))
                .andExpect(jsonPath("$.lastExecution.id").value(Integer.parseInt(executionId)))
                .andExpect(jsonPath("$.lastExecution.status").value("SUCCESS"));

        Path archive = Path.of("target/test-data/executions/" + executionId);
        assertThat(archive).isDirectory();
        mvc.perform(delete("/api/sources/" + sourceId)).andExpect(status().isNoContent());
        assertThat(archive).doesNotExist();
        mvc.perform(get("/api/executions/" + executionId)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Échec : statut FAILED avec le message, source tout de même archivée ; source désactivée refusée")
    void failedExecution(@TempDir Path dir) throws Exception {
        String sourceId = createSource("test-exec-ko", dir, slash(dir.resolve("absent.json")));
        try {
            String executionId = runAndWait(sourceId);
            mvc.perform(get("/api/executions/" + executionId))
                    .andExpect(jsonPath("$.status").value("FAILED"))
                    .andExpect(jsonPath("$.errorMessage").value(containsString("absent.json")))
                    .andExpect(jsonPath("$.failedStep").value("MAPPING"))
                    .andExpect(jsonPath("$.errorCause").value(containsString("FileNotFoundException")))
                    .andExpect(jsonPath("$.errorStack").value(containsString("FtmGeneratorService")))
                    .andExpect(jsonPath("$.files[3].size").value(nullValue()))
                    .andExpect(jsonPath("$.files[0].size").isNumber())
                    .andExpect(jsonPath("$.files[2].size").value(nullValue()));
            mvc.perform(get("/api/executions/" + executionId + "/files/mapping")).andExpect(status().isNotFound());

            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                            .patch("/api/sources/" + sourceId + "/enabled")
                            .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\": false}"))
                    .andExpect(status().isOk());
            mvc.perform(post("/api/sources/" + sourceId + "/executions"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value(containsString("désactivée")));
        } finally {
            mvc.perform(delete("/api/sources/" + sourceId));
        }
    }
}
