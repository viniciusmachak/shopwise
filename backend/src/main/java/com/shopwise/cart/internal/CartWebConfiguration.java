package com.shopwise.cart.internal;

import java.util.List;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.shopwise.cart.CartService;

@Configuration(proxyBeanMethods = false)
class CartWebConfiguration implements WebMvcConfigurer {

    private final CartService carts;

    CartWebConfiguration(@Lazy CartService carts) {
        this.carts = carts;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new CartKeyArgumentResolver(carts));
    }
}
