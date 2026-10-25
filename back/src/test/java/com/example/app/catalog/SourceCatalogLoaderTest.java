package com.example.app.catalog;

import com.example.app.core.DataSourceConfig;
import com.example.app.core.repository.DataSourceConfigRepository;
import com.example.app.core.repository.ParserCoverageRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class SourceCatalogLoaderTest {

    @Autowired
    private SourceCatalogLoader loader;

    @Autowired
    private DataSourceConfigRepository sourceRepository;

    @Autowired
    private ParserCoverageRepository coverageRepository;

    private SourceDefinition chamber() {
        return loader.definition("lu_pep_chamber").orElseThrow();
    }

    private DataSourceConfig chamberConfig() {
        return sourceRepository.findBySourceName("lu_pep_chamber").orElseThrow();
    }

    @Test
    @DisplayName("Une source non personnalisée suit le catalogue au démarrage")
    void catalogUpdatesNonCustomizedSource() {
        DataSourceConfig source = chamberConfig();
        source.setExternalSourcePath("https://ancienne-adresse.example/fichier.csv");
        source.setLabel("Ancien libellé");
        source.setCustomized(false);
        sourceRepository.save(source);

        loader.sync(chamber());

        DataSourceConfig synced = chamberConfig();
        assertThat(synced.getExternalSourcePath()).isEqualTo(String.join("\n", chamber().urls()));
        assertThat(synced.getLabel()).isEqualTo(chamber().label());
    }

    @Test
    @DisplayName("Une source modifiée dans l'interface n'est plus touchée par le catalogue")
    void customizedSourceIsKept() {
        DataSourceConfig source = chamberConfig();
        source.setExternalSourcePath("https://miroir.example/deputes.csv");
        source.setLabel("Mon libellé");
        source.setCustomized(true);
        source.setEnabled(false);
        sourceRepository.save(source);

        loader.sync(chamber());

        DataSourceConfig synced = chamberConfig();
        assertThat(synced.getExternalSourcePath()).isEqualTo("https://miroir.example/deputes.csv");
        assertThat(synced.getLabel()).isEqualTo("Mon libellé");
        assertThat(synced.isEnabled()).isFalse();
    }

    @Test
    @DisplayName("Une source supprimée n'est pas recréée par le catalogue")
    void deletedSourceIsNotRecreated() {
        DataSourceConfig source = chamberConfig();
        source.setDeletedAt(LocalDateTime.now());
        source.setCustomized(false);
        sourceRepository.save(source);

        loader.sync(chamber());

        assertThat(chamberConfig().getDeletedAt()).isNotNull();
    }

    @Test
    @DisplayName("Réinitialiser une source personnalisée la remet dans l'état du catalogue, activation gardée")
    void resetRestoresCatalogDefinition() {
        DataSourceConfig source = chamberConfig();
        source.setExternalSourcePath("https://miroir.example/deputes.csv");
        source.setMappingFilePath(null);
        source.setCustomized(true);
        source.setEnabled(false);
        sourceRepository.save(source);
        coverageRepository.deleteAll(coverageRepository.findByDataSourceConfigId(source.getId()));

        DataSourceConfig reset = loader.reset(source);

        assertThat(reset.isCustomized()).isFalse();
        assertThat(reset.isEnabled()).isFalse();
        assertThat(reset.getExternalSourcePath()).isEqualTo(String.join("\n", chamber().urls()));
        assertThat(reset.getMappingFilePath()).isEqualTo(chamber().mapping());
        assertThat(coverageRepository.findByDataSourceConfigId(reset.getId()))
                .extracting(c -> c.getPepFunction().getLabel())
                .containsExactly("Député");
    }
}
