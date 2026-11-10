CREATE TABLE category (
    id   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    slug VARCHAR(80)  NOT NULL UNIQUE,
    name VARCHAR(120) NOT NULL
);

CREATE TABLE product (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    slug        VARCHAR(160)  NOT NULL UNIQUE,
    name        VARCHAR(200)  NOT NULL,
    brand       VARCHAR(120)  NOT NULL,
    description TEXT          NOT NULL,
    category_id BIGINT        NOT NULL REFERENCES category (id),
    price       NUMERIC(12,2) NOT NULL CHECK (price >= 0),
    currency    VARCHAR(3)    NOT NULL DEFAULT 'USD',
    attributes  JSONB         NOT NULL DEFAULT '{}'::jsonb,
    image_url   VARCHAR(500),
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX product_category_idx ON product (category_id);

CREATE TABLE product_variant (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    product_id     BIGINT        NOT NULL REFERENCES product (id) ON DELETE CASCADE,
    sku            VARCHAR(80)   NOT NULL UNIQUE,
    size           VARCHAR(20),
    color          VARCHAR(40),
    price_override NUMERIC(12,2) CHECK (price_override >= 0)
);

CREATE INDEX product_variant_product_idx ON product_variant (product_id);

CREATE TABLE inventory (
    variant_id BIGINT PRIMARY KEY REFERENCES product_variant (id) ON DELETE CASCADE,
    quantity   INT NOT NULL CHECK (quantity >= 0)
);
