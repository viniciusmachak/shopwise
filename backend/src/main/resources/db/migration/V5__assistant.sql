CREATE TABLE assistant_conversation (
    id         UUID PRIMARY KEY,
    cart_key   UUID        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX assistant_conversation_cart_idx ON assistant_conversation (cart_key);

CREATE TABLE audit_log (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    cart_key        UUID         NOT NULL,
    conversation_id UUID,
    action          VARCHAR(80)  NOT NULL,
    arguments       JSONB        NOT NULL,
    outcome         VARCHAR(20)  NOT NULL,
    detail          TEXT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
