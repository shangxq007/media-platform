-- Effect packs: system builtin + tenant custom packs (catalog single source for API/frontend)
CREATE TABLE IF NOT EXISTS effect_pack (
    id              VARCHAR(64)  PRIMARY KEY,
    pack_id         VARCHAR(128) NOT NULL,
    version         VARCHAR(32)  NOT NULL,
    name            VARCHAR(255) NOT NULL,
    description     VARCHAR(1024),
    author          VARCHAR(128),
    compatibility   VARCHAR(32)  DEFAULT '2.0',
    allowed_tiers   text,
    tenant_id       VARCHAR(64)  NOT NULL DEFAULT '',
    builtin         BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_effect_pack_identity
    ON effect_pack (pack_id, version, tenant_id);

CREATE INDEX IF NOT EXISTS idx_effect_pack_tenant ON effect_pack (tenant_id);

CREATE TABLE IF NOT EXISTS effect_pack_effect (
    id                  VARCHAR(64)  PRIMARY KEY,
    pack_row_id         VARCHAR(64)  NOT NULL,
    effect_key          VARCHAR(128) NOT NULL,
    display_name        VARCHAR(255) NOT NULL,
    category            VARCHAR(64)  NOT NULL,
    description         VARCHAR(1024),
    parameter_schema    text,
    default_values      text,
    provider_mappings   text,
    allowed_tiers       text,
    sort_order          INT          NOT NULL DEFAULT 0,
    CONSTRAINT fk_effect_pack_effect_pack FOREIGN KEY (pack_row_id) REFERENCES effect_pack(id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_effect_pack_effect_key
    ON effect_pack_effect (pack_row_id, effect_key);
