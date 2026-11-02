-- Escopo do usuário por destino (CD e/ou loja). Usuários existentes continuam vendo todos os destinos.
ALTER TABLE usuario ADD COLUMN todos_destinos BOOLEAN NOT NULL DEFAULT TRUE;

CREATE TABLE usuario_destino (
    id         BIGSERIAL PRIMARY KEY,
    usuario_id BIGINT NOT NULL REFERENCES usuario (id) ON DELETE CASCADE,
    cd_id      BIGINT REFERENCES centro_distribuicao (id),
    loja       VARCHAR(80),
    CONSTRAINT ck_usuario_destino_um CHECK ((cd_id IS NULL) <> (loja IS NULL))
);
CREATE UNIQUE INDEX ux_usuario_destino_cd ON usuario_destino (usuario_id, cd_id) WHERE cd_id IS NOT NULL;
CREATE UNIQUE INDEX ux_usuario_destino_loja ON usuario_destino (usuario_id, loja) WHERE loja IS NOT NULL;

-- Aviso do sino ligado ao destino do agendamento (nulo = aviso geral, todos veem)
ALTER TABLE aviso ADD COLUMN cd_id BIGINT;
ALTER TABLE aviso ADD COLUMN loja VARCHAR(80);
