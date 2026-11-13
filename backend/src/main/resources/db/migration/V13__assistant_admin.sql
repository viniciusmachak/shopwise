-- Runtime settings changed from the backoffice, e.g. the assistant's chat mode.
CREATE TABLE app_setting (
    key        VARCHAR(80)  PRIMARY KEY,
    value      VARCHAR(500) NOT NULL,
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by BIGINT
);

-- Chat model use per day and provider, for the assistant dashboard. Token counts are as
-- reported by the provider and approximate when tools run several model calls in one turn.
CREATE TABLE assistant_llm_usage (
    day               DATE        NOT NULL,
    provider          VARCHAR(40) NOT NULL,
    requests          INT         NOT NULL DEFAULT 0,
    failures          INT         NOT NULL DEFAULT 0,
    prompt_tokens     BIGINT      NOT NULL DEFAULT 0,
    completion_tokens BIGINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (day, provider)
);
