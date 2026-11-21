package com.shopwise.payment.internal;

import java.time.Clock;

import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class PaymentConfiguration {

    @Bean
    @ConditionalOnMissingBean
    StripeCheckout stripeCheckout(StripeProperties properties) {
        return new StripeClientCheckout(properties.enabled() ? properties.secretKey() : "sk_unset");
    }

    @Bean
    PaymentGateway paymentGateway(StripeProperties stripe, SimulatedPaymentProperties simulated,
            StripeCheckout checkout) {
        if (stripe.enabled()) {
            LoggerFactory.getLogger(PaymentConfiguration.class).info("Payments go through Stripe Checkout");
            return new StripePaymentGateway(checkout, stripe, Clock.systemUTC());
        }
        return new SimulatedPaymentGateway(simulated);
    }
}
