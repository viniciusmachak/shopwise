package com.shopwise.recommendation.internal;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.shopwise.recommendation.RecommendationService;
import com.shopwise.recommendation.Recommendations;

@RestController
class RecommendationController {

    private final RecommendationService recommendations;

    RecommendationController(RecommendationService recommendations) {
        this.recommendations = recommendations;
    }

    @GetMapping("/api/products/{id}/recommendations")
    Recommendations forProduct(@PathVariable long id, @RequestParam(defaultValue = "4") int limit) {
        return recommendations.forProduct(id, limit)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found."));
    }
}
