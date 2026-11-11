package com.shopwise.notification.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import com.shopwise.customer.CustomerContact;
import com.shopwise.customer.CustomerDirectory;
import com.shopwise.customer.PasswordResetRequested;
import com.shopwise.order.OrderCancelled;
import com.shopwise.order.OrderPlaced;
import com.shopwise.order.OrderService;

/**
 * Sends e-mails after the triggering transaction commits. If sending fails, the event
 * publication stays incomplete and Spring Modulith can resubmit it.
 */
@Component
class EmailNotifications {

    private static final Logger log = LoggerFactory.getLogger(EmailNotifications.class);

    private final JavaMailSender mail;
    private final CustomerDirectory customers;
    private final OrderService orders;
    private final MailProperties properties;
    private final String frontendUrl;

    EmailNotifications(JavaMailSender mail, CustomerDirectory customers, OrderService orders, MailProperties properties,
            @Value("${shopwise.web.frontend-url:http://localhost:5173}") String frontendUrl) {
        this.mail = mail;
        this.customers = customers;
        this.orders = orders;
        this.properties = properties;
        this.frontendUrl = frontendUrl.replaceAll("/$", "");
    }

    @ApplicationModuleListener
    void on(PasswordResetRequested event) {
        customers.contact(event.customerId())
                .ifPresent(to -> send(to, Emails.passwordReset(to, event.resetLink())));
    }

    @ApplicationModuleListener
    void on(OrderPlaced event) {
        var to = customers.contact(event.customerId());
        var order = orders.find(event.customerId(), event.orderId());
        if (to.isPresent() && order.isPresent()) {
            send(to.get(), Emails.orderPlaced(to.get(), order.get(), frontendUrl + "/orders/" + event.orderId()));
        }
    }

    @ApplicationModuleListener
    void on(OrderCancelled event) {
        var to = customers.contact(event.customerId());
        var order = orders.find(event.customerId(), event.orderId());
        if (to.isPresent() && order.isPresent()) {
            send(to.get(), Emails.orderCancelled(to.get(), order.get()));
        }
    }

    private void send(CustomerContact to, Emails.Email email) {
        var message = new SimpleMailMessage();
        message.setFrom(properties.from());
        message.setTo(to.email());
        message.setSubject(email.subject());
        message.setText(email.body());
        mail.send(message);
        log.info("Sent \"{}\" to customer {}", email.subject(), to.id());
    }
}
