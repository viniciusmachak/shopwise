package com.shopwise.recommendation.internal;

import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param complements for each category slug, the categories whose products go well with it;
 *                    used when there isn't enough order history yet
 */
@ConfigurationProperties("shopwise.recommendations")
public record RecommendationProperties(Map<String, List<String>> complements) {

    public RecommendationProperties {
        complements = complements == null ? Map.of() : complements;
    }
}
