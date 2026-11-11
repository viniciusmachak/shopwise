package com.shopwise.cart.internal;

import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import com.shopwise.cart.CartKey;
import com.shopwise.cart.CartService;
import com.shopwise.customer.CurrentCustomer;
import com.shopwise.customer.CustomerRef;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Resolves {@link CartKey} parameters: the signed-in customer's cart, or the anonymous cart
 * from the cookie. Updates the cookie whenever the resolved cart differs from it.
 */
class CartKeyArgumentResolver implements HandlerMethodArgumentResolver {

    static final String COOKIE = "shopwise_cart";

    private static final String ATTRIBUTE = CartKey.class.getName();

    private final CartService carts;

    CartKeyArgumentResolver(CartService carts) {
        this.carts = carts;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterType() == CartKey.class;
    }

    @Override
    public CartKey resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        var request = webRequest.getNativeRequest(HttpServletRequest.class);
        var cached = (CartKey) request.getAttribute(ATTRIBUTE);
        if (cached != null) {
            return cached;
        }
        var cookieKey = fromCookie(request).orElse(null);
        var customerId = CurrentCustomer.get().map(CustomerRef::id).orElse(null);
        var key = carts.resolve(customerId, cookieKey);
        if (!key.equals(cookieKey)) {
            var cookie = ResponseCookie.from(COOKIE, key.value().toString())
                    .httpOnly(true)
                    .secure(request.isSecure())
                    .sameSite("Lax")
                    .path("/")
                    .maxAge(Duration.ofDays(30))
                    .build();
            webRequest.getNativeResponse(HttpServletResponse.class).addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        }
        request.setAttribute(ATTRIBUTE, key);
        return key;
    }

    private static Optional<CartKey> fromCookie(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return Optional.empty();
        }
        return Arrays.stream(request.getCookies())
                .filter(c -> COOKIE.equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .flatMap(CartKeyArgumentResolver::parse);
    }

    private static Optional<CartKey> parse(String value) {
        try {
            return Optional.of(new CartKey(UUID.fromString(value)));
        }
        catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
