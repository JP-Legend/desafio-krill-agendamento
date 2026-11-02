-- V13: um único lugar para os limites de recebimento (CD ou loja) e calendário de recebimento por destino.
-- Aplica sobre a V12 sem recriar o banco.

-- 1) Limite por faixa também na capacidade por destino (antes ficava no cadastro do CD).
ALTER TABLE capacidade_recebimento ADD COLUMN paletes_faixa INTEGER CHECK (paletes_faixa > 0);

DO $$
DECLARE r record;
BEGIN
    FOR r IN SELECT conname FROM pg_constraint
             WHERE conrelid = 'capacidade_recebimento'::regclass AND contype = 'c'
               AND pg_get_constraintdef(oid) LIKE '%paletes IS NOT NULL%caminhoes IS NOT NULL%'
    LOOP
        EXECUTE format('ALTER TABLE capacidade_recebimento DROP CONSTRAINT %I', r.conname);
    END LOOP;
END $$;
ALTER TABLE capacidade_recebimento ADD CONSTRAINT ck_capacidade_algum_limite
    CHECK (paletes IS NOT NULL OR caminhoes IS NOT NULL OR paletes_faixa IS NOT NULL);

-- 2) Os limites que estavam no cadastro do CD passam para a regra geral (todos os dias) do CD.
--    Onde já havia regra geral cadastrada, ela continua valendo para o dia; o limite por faixa é copiado.
UPDATE capacidade_recebimento c
   SET paletes = coalesce(c.paletes, cd.capacidade_diaria_paletes),
       paletes_faixa = coalesce(c.paletes_faixa, cd.capacidade_faixa_paletes)
  FROM centro_distribuicao cd
 WHERE c.cd_id = cd.id AND c.dia_semana IS NULL
   AND (cd.capacidade_diaria_paletes IS NOT NULL OR cd.capacidade_faixa_paletes IS NOT NULL);

INSERT INTO capacidade_recebimento (cd_id, paletes, paletes_faixa)
SELECT cd.id, cd.capacidade_diaria_paletes, cd.capacidade_faixa_paletes
  FROM centro_distribuicao cd
 WHERE (cd.capacidade_diaria_paletes IS NOT NULL OR cd.capacidade_faixa_paletes IS NOT NULL)
   AND NOT EXISTS (SELECT 1 FROM capacidade_recebimento c WHERE c.cd_id = cd.id AND c.dia_semana IS NULL);

ALTER TABLE centro_distribuicao DROP COLUMN capacidade_diaria_paletes;
ALTER TABLE centro_distribuicao DROP COLUMN capacidade_faixa_paletes;

-- 3) Calendário de recebimento: dias da semana (segunda..domingo, '1' = recebe) e se recebe em feriado.
--    Destino sem calendário cadastrado recebe todos os dias, inclusive feriados (comportamento anterior).
CREATE TABLE calendario_recebimento (
    id             BIGSERIAL PRIMARY KEY,
    cd_id          BIGINT REFERENCES centro_distribuicao (id),
    loja           VARCHAR(80),
    dias_semana    CHAR(7) NOT NULL DEFAULT '1111110' CHECK (dias_semana ~ '^[01]{7}$'),
    recebe_feriado BOOLEAN NOT NULL DEFAULT FALSE,
    CHECK ((cd_id IS NULL) <> (loja IS NULL))
);
CREATE UNIQUE INDEX ux_calendario_destino ON calendario_recebimento (coalesce(cd_id, 0), lower(coalesce(loja, '')));

-- Datas específicas fechadas (ex.: inventário) ou abertas (ex.: feriado em que o destino recebe).
CREATE TABLE excecao_recebimento (
    id      BIGSERIAL PRIMARY KEY,
    cd_id   BIGINT REFERENCES centro_distribuicao (id),
    loja    VARCHAR(80),
    data    DATE NOT NULL,
    aberto  BOOLEAN NOT NULL,
    motivo  VARCHAR(120),
    CHECK ((cd_id IS NULL) <> (loja IS NULL))
);
CREATE UNIQUE INDEX ux_excecao_destino_data ON excecao_recebimento (coalesce(cd_id, 0), lower(coalesce(loja, '')), data);

-- Feriados nacionais (Lei 662/1949, Lei 6.802/1980, Lei 14.759/2023) e pontos facultativos federais de 2026 e 2027.
CREATE TABLE feriado (
    data DATE PRIMARY KEY,
    nome VARCHAR(80) NOT NULL
);
INSERT INTO feriado (data, nome) VALUES
    ('2026-01-01', 'Confraternização Universal'),
    ('2026-02-16', 'Carnaval (ponto facultativo)'),
    ('2026-02-17', 'Carnaval (ponto facultativo)'),
    ('2026-04-03', 'Paixão de Cristo'),
    ('2026-04-21', 'Tiradentes'),
    ('2026-05-01', 'Dia do Trabalho'),
    ('2026-06-04', 'Corpus Christi (ponto facultativo)'),
    ('2026-09-07', 'Independência do Brasil'),
    ('2026-10-12', 'Nossa Senhora Aparecida'),
    ('2026-11-02', 'Finados'),
    ('2026-11-15', 'Proclamação da República'),
    ('2026-11-20', 'Dia Nacional de Zumbi e da Consciência Negra'),
    ('2026-12-25', 'Natal'),
    ('2027-01-01', 'Confraternização Universal'),
    ('2027-02-08', 'Carnaval (ponto facultativo)'),
    ('2027-02-09', 'Carnaval (ponto facultativo)'),
    ('2027-03-26', 'Paixão de Cristo'),
    ('2027-04-21', 'Tiradentes'),
    ('2027-05-01', 'Dia do Trabalho'),
    ('2027-05-27', 'Corpus Christi (ponto facultativo)'),
    ('2027-09-07', 'Independência do Brasil'),
    ('2027-10-12', 'Nossa Senhora Aparecida'),
    ('2027-11-02', 'Finados'),
    ('2027-11-15', 'Proclamação da República'),
    ('2027-11-20', 'Dia Nacional de Zumbi e da Consciência Negra'),
    ('2027-12-25', 'Natal')
ON CONFLICT (data) DO NOTHING;
