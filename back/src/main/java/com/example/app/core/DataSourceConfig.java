package com.example.app.core;

import com.example.app.ingestion.model.SourceFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataSourceConfig {

    private SourceFormat sourceFormat;

    private String externalSourcePath;

    private String localSourcePath;

    private String initialJsonSourcePath;

    private String mappingFilePath;

    private String ftmOutputPath;

}
