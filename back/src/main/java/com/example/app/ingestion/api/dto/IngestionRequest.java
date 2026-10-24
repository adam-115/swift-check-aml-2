package com.example.app.ingestion.api.dto;

import com.example.app.ingestion.model.FragmentMappingRule;
import com.example.app.ingestion.model.SourceFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IngestionRequest {
    private SourceFormat format;
    private String content;
    private FragmentMappingRule rule;
}
