CREATE TABLE coupon (
    code        VARCHAR(40) PRIMARY KEY,
    percent_off INT         NOT NULL CHECK (percent_off BETWEEN 1 AND 100),
    active      BOOLEAN     NOT NULL DEFAULT true,
    expires_at  TIMESTAMPTZ
);

CREATE TABLE cart (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    cart_key    UUID        NOT NULL UNIQUE,
    coupon_code VARCHAR(40) REFERENCES coupon (code),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE cart_item (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    cart_id    BIGINT NOT NULL REFERENCES cart (id) ON DELETE CASCADE,
    variant_id BIGINT NOT NULL,
    quantity   INT    NOT NULL CHECK (quantity > 0),
    UNIQUE (cart_id, variant_id)
);
