package com.shopwise.order;

import java.util.List;

/**
 * Read-only facts derived from orders, for recommendations.
 */
public interface OrderInsights {

    /** Other products that appear most often in the same orders as this one (paid or later). */
    List<Long> boughtTogether(long productId, int limit);
}
