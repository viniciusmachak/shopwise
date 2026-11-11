package com.shopwise.payment.internal;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shopwise.catalog.Money;
import com.shopwise.payment.PaymentResult;
import com.shopwise.payment.PaymentService;

@Service
@Transactional
class DefaultPaymentService implements PaymentService {

    private final PaymentGateway gateway;
    private final JdbcClient jdbc;

    DefaultPaymentService(PaymentGateway gateway, JdbcClient jdbc) {
        this.gateway = gateway;
        this.jdbc = jdbc;
    }

    @Override
    public PaymentResult charge(long orderId, Money amount) {
        var result = gateway.charge(orderId, amount);
        jdbc.sql("insert into payment (order_id, amount, currency, status, reference) values (:order, :amount, :currency, :status, :ref)")
                .param("order", orderId)
                .param("amount", amount.amount())
                .param("currency", amount.currency())
                .param("status", result.approved() ? "APPROVED" : "DECLINED")
                .param("ref", result.reference())
                .update();
        return result;
    }

    @Override
    public void refund(long orderId) {
        var references = jdbc.sql("select reference from payment where order_id = :order and status = 'APPROVED'")
                .param("order", orderId)
                .query(String.class)
                .list();
        references.forEach(gateway::refund);
        jdbc.sql("update payment set status = 'REFUNDED', updated_at = now() where order_id = :order and status = 'APPROVED'")
                .param("order", orderId)
                .update();
    }
}
