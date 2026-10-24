package com.example.app.ingestion.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Root configuration model holding mapping rules for various sanction sources and entities.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IngestionConfig {

    private String name;
    private String description;

    @Builder.Default
    private List<FragmentMappingRule> rules = new ArrayList<>();

    public Optional<FragmentMappingRule> findRuleByTargetNode(String targetNodeName) {
        if (targetNodeName == null || rules == null) {
            return Optional.empty();
        }
        return rules.stream()
                .filter(r -> targetNodeName.equalsIgnoreCase(r.getTargetNodeName()))
                .findFirst();
    }
}
