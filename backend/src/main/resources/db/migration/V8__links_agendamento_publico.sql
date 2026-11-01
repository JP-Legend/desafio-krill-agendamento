-- Links públicos de agendamento (porte de /recebimento/agendamentos/links do Gestão CD):
-- um link fixo por CD, por loja ou para todos os CDs. Gerar de novo desativa o link ativo do mesmo destino.

CREATE TABLE link_agendamento (
    id           BIGSERIAL PRIMARY KEY,
    token        VARCHAR(64)  NOT NULL UNIQUE,
    cd_id        BIGINT REFERENCES centro_distribuicao (id),
    loja         VARCHAR(80),
    ativo        BOOLEAN      NOT NULL DEFAULT TRUE,
    criado_por   VARCHAR(60)  NOT NULL,
    criado_em    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    revogado_por VARCHAR(60),
    revogado_em  TIMESTAMPTZ,
    CHECK (cd_id IS NULL OR loja IS NULL)
);
CREATE INDEX ix_link_agendamento_ativo ON link_agendamento (ativo, cd_id, lower(loja));

-- Permissão 1107 (Links de agendamento): só o Administrador
INSERT INTO perfil_permissao (perfil_id, codigo)
SELECT id, 1107 FROM perfil_acesso WHERE nome = 'Administrador';
