CREATE TABLE orders (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    customer_id      BIGINT        NOT NULL,
    status           VARCHAR(20)   NOT NULL,
    subtotal         NUMERIC(12,2) NOT NULL,
    discount         NUMERIC(12,2) NOT NULL,
    shipping         NUMERIC(12,2) NOT NULL,
    total            NUMERIC(12,2) NOT NULL,
    currency         VARCHAR(3)    NOT NULL,
    coupon_code      VARCHAR(40),
    shipping_option  VARCHAR(20)   NOT NULL,
    ship_recipient   VARCHAR(120)  NOT NULL,
    ship_line1       VARCHAR(200)  NOT NULL,
    ship_line2       VARCHAR(200),
    ship_city        VARCHAR(100)  NOT NULL,
    ship_state       VARCHAR(100)  NOT NULL,
    ship_postal_code VARCHAR(20)   NOT NULL,
    ship_country     VARCHAR(2)    NOT NULL,
    idempotency_key  VARCHAR(100)  NOT NULL,
    placed_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    cancelled_at     TIMESTAMPTZ,
    UNIQUE (customer_id, idempotency_key)
);

CREATE INDEX orders_customer_idx ON orders (customer_id, placed_at DESC);

CREATE TABLE order_item (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id     BIGINT        NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    variant_id   BIGINT        NOT NULL,
    product_id   BIGINT        NOT NULL,
    product_name VARCHAR(200)  NOT NULL,
    size         VARCHAR(20),
    color        VARCHAR(40),
    unit_price   NUMERIC(12,2) NOT NULL,
    quantity     INT           NOT NULL CHECK (quantity > 0),
    line_total   NUMERIC(12,2) NOT NULL
);

CREATE INDEX order_item_order_idx ON order_item (order_id);

CREATE TABLE payment (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id   BIGINT        NOT NULL,
    amount     NUMERIC(12,2) NOT NULL,
    currency   VARCHAR(3)    NOT NULL,
    status     VARCHAR(20)   NOT NULL,
    reference  VARCHAR(80)   NOT NULL,
    created_at TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX payment_order_idx ON payment (order_id);
