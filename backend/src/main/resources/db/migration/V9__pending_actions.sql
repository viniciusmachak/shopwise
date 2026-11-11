-- Irreversible assistant actions wait here until the customer confirms them.
CREATE TABLE pending_action (
    token           VARCHAR(64) PRIMARY KEY,
    type            VARCHAR(20) NOT NULL,
    customer_id     BIGINT      NOT NULL,
    cart_key        UUID        NOT NULL,
    conversation_id UUID,
    address_id      BIGINT,
    shipping        VARCHAR(20),
    cart_hash       VARCHAR(64),
    order_id        BIGINT,
    result_order_id BIGINT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at      TIMESTAMPTZ NOT NULL,
    used_at         TIMESTAMPTZ,
    discarded_at    TIMESTAMPTZ
);

CREATE INDEX pending_action_customer_idx ON pending_action (customer_id);

ALTER TABLE assistant_conversation ADD COLUMN customer_id BIGINT;
CREATE INDEX assistant_conversation_customer_idx ON assistant_conversation (customer_id);
