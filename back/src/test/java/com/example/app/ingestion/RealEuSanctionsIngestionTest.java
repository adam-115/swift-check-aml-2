package com.example.app.ingestion;

import com.example.app.ingestion.converter.XmlSourceConverter;
import com.example.app.ingestion.engine.DynamicFtmMapper;
import com.example.app.ingestion.model.FtmEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RealEuSanctionsIngestionTest {

    private final XmlSourceConverter xmlConverter = new XmlSourceConverter();
    private final DynamicFtmMapper dynamicFtmMapper = new DynamicFtmMapper();

    @Test
    @DisplayName("Test complet d'ingestion et de mapping avec le VRAI fichier de sanctions officiel de l'UE")
    void testRealEuSanctionsFileIngestion() throws Exception {
        File realXmlFile = new File("src/test/resources/eu-sanctions-full.xml");
        assertThat(realXmlFile).exists();
        assertThat(realXmlFile.length()).isGreaterThan(1_000_000L);

        long startTime = System.currentTimeMillis();

        // 1. Conversion XML -> JSON dynamique
        List<Map<String, Object>> dynamicJsonRecords;
        try (InputStream is = new FileInputStream(realXmlFile)) {
            dynamicJsonRecords = xmlConverter.convertToDynamicJson(is);
        }

        long parseTime = System.currentTimeMillis();
        System.out.printf("Conversion XML -> JSON terminée en %d ms. Nombre d'enregistrements: %d%n",
                (parseTime - startTime), dynamicJsonRecords.size());

        assertThat(dynamicJsonRecords)
                .as("La liste des enregistrements convertis ne doit pas être vide")
                .isNotEmpty();

        // 2. Mapping vers FollowTheMoney (FTM)
        List<FtmEntity> ftmEntities;
        try (InputStream mappingIstream = getClass().getResourceAsStream("/eu-sanctions-mapping.json")) {
            assertThat(mappingIstream).isNotNull();
            ftmEntities = dynamicFtmMapper.map(dynamicJsonRecords, mappingIstream);
        }

        long mapTime = System.currentTimeMillis();
        System.out.printf("Mapping FTM terminé en %d ms. Nombre d'entités FTM générées: %d%n",
                (mapTime - parseTime), ftmEntities.size());

        // 3. Vérifications & Assertions
        assertThat(ftmEntities)
                .as("Des entités FTM doivent être générées à partir du fichier officiel")
                .isNotEmpty()
                .hasSizeGreaterThan(1000);

        // Compter par schéma FTM
        long personCount = ftmEntities.stream().filter(e -> "Person".equals(e.getSchema())).count();
        long companyCount = ftmEntities.stream().filter(e -> "Company".equals(e.getSchema())).count();
        long vesselCount = ftmEntities.stream().filter(e -> "Vessel".equals(e.getSchema())).count();

        System.out.printf("Répartition FTM -> Personnes: %d, Entreprises: %d, Navires: %d%n",
                personCount, companyCount, vesselCount);

        assertThat(personCount).isGreaterThan(4000);
        assertThat(companyCount).isGreaterThan(1500);

        // Vérifier la conformité d'une entité Personne
        FtmEntity samplePerson = ftmEntities.stream()
                .filter(e -> "Person".equals(e.getSchema()) && e.getProperties().containsKey("birthDate"))
                .findFirst()
                .orElseThrow();

        assertThat(samplePerson.getId())
                .as("L'identifiant doit être un hash SHA-1 de 40 caractères hex")
                .matches("^[a-f0-9]{40}$");
        assertThat(samplePerson.getProperties().get("name")).isNotEmpty();
        assertThat(samplePerson.getProperties().get("birthDate"))
                .as("La date de naissance doit être normalisée au format ISO-8601 (YYYY-MM-DD ou YYYY)")
                .allMatch(d -> d.matches("^\\d{4}(-\\d{2}(-\\d{2})?)?$"));

        // Vérifier une entité Entreprise
        FtmEntity sampleCompany = ftmEntities.stream()
                .filter(e -> "Company".equals(e.getSchema()))
                .findFirst()
                .orElseThrow();

        assertThat(sampleCompany.getId()).matches("^[a-f0-9]{40}$");
        assertThat(sampleCompany.getProperties().get("name")).isNotEmpty();

        System.out.printf("Exemple Personne FTM: ID=%s, Nom=%s, DateNaissance=%s, Nationalite=%s%n",
                samplePerson.getId(),
                samplePerson.getProperties().get("name"),
                samplePerson.getProperties().get("birthDate"),
                samplePerson.getProperties().get("nationality"));

        System.out.printf("Exemple Entreprise FTM: ID=%s, Nom=%s, Pays=%s%n",
                sampleCompany.getId(),
                sampleCompany.getProperties().get("name"),
                sampleCompany.getProperties().get("country"));
    }
}
