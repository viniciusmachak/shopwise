package com.shopwise.recommendation;

import java.util.Optional;

/**
 * Public API of the recommendation module.
 */
public interface RecommendationService {

    /** Empty when the product doesn't exist or isn't for sale. */
    Optional<Recommendations> forProduct(long productId, int limit);
}
