package com.shopwise;

import java.util.ArrayList;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.shopwise.cart.CartKey;

/**
 * API DTOs are records whose fields are always present. Marks every property as required
 * so generated frontend types are exact; fields annotated {@code @Nullable} are nullable.
 */
@Configuration(proxyBeanMethods = false)
class OpenApiConfiguration {

    static {
        // Resolved from the cart cookie on the server, not a request parameter.
        SpringDocUtils.getConfig().addRequestWrapperToIgnore(CartKey.class);
    }

    @Bean
    OpenApiCustomizer requireAllProperties() {
        return openApi -> {
            if (openApi.getComponents() == null || openApi.getComponents().getSchemas() == null) {
                return;
            }
            openApi.getComponents().getSchemas().values().forEach(schema -> {
                if (schema.getProperties() != null && !schema.getProperties().isEmpty()) {
                    schema.setRequired(new ArrayList<>(schema.getProperties().keySet()));
                }
            });
        };
    }
}
