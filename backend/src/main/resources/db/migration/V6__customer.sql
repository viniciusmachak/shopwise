CREATE TABLE customer (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email          VARCHAR(254) NOT NULL,
    name           VARCHAR(120) NOT NULL,
    password_hash  VARCHAR(100),
    google_subject VARCHAR(255) UNIQUE,
    role           VARCHAR(20)  NOT NULL DEFAULT 'CUSTOMER',
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX customer_email_idx ON customer (lower(email));

CREATE TABLE address (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    customer_id BIGINT       NOT NULL REFERENCES customer (id) ON DELETE CASCADE,
    recipient   VARCHAR(120) NOT NULL,
    line1       VARCHAR(200) NOT NULL,
    line2       VARCHAR(200),
    city        VARCHAR(100) NOT NULL,
    state       VARCHAR(100) NOT NULL,
    postal_code VARCHAR(20)  NOT NULL,
    country     VARCHAR(2)   NOT NULL DEFAULT 'US',
    is_default  BOOLEAN      NOT NULL DEFAULT false,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX address_customer_idx ON address (customer_id);
CREATE UNIQUE INDEX address_one_default_idx ON address (customer_id) WHERE is_default;

CREATE TABLE password_reset_token (
    token_hash  VARCHAR(64) PRIMARY KEY,
    customer_id BIGINT      NOT NULL REFERENCES customer (id) ON DELETE CASCADE,
    expires_at  TIMESTAMPTZ NOT NULL,
    used_at     TIMESTAMPTZ
);
