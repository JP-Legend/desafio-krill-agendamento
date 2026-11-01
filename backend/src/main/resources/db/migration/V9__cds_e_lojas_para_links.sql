-- Links de agendamento por CD e loja: os CDs do Gestão CD (801, 805 e 806) e um cadastro mínimo de lojas.
-- Antes, a instalação nova só tinha o CD 806 e as lojas existiam apenas como texto na capacidade.

INSERT INTO centro_distribuicao (codigo, nome)
SELECT v.codigo, v.nome FROM (VALUES ('801', 'CD 801'), ('805', 'CD 805')) AS v(codigo, nome)
WHERE NOT EXISTS (SELECT 1 FROM centro_distribuicao c WHERE c.codigo = v.codigo);

CREATE TABLE loja (
    id     BIGSERIAL PRIMARY KEY,
    nome   VARCHAR(80) NOT NULL,
    cd_id  BIGINT REFERENCES centro_distribuicao (id),
    ativo  BOOLEAN     NOT NULL DEFAULT TRUE
);
CREATE UNIQUE INDEX uk_loja_nome ON loja (lower(nome));

-- Lojas já usadas na capacidade, na entrega direta e nos romaneios de paletes
INSERT INTO loja (nome)
SELECT DISTINCT ON (lower(trim(l))) trim(l) FROM (
    SELECT loja AS l FROM capacidade_recebimento
    UNION ALL SELECT loja FROM agendamento_logistica
    UNION ALL SELECT loja FROM movimento_palete
) x WHERE l IS NOT NULL AND trim(l) <> ''
ORDER BY lower(trim(l)), trim(l);

-- Instalação nova: três lojas de exemplo para o link de agendamento (editáveis em Cadastros → Lojas)
INSERT INTO loja (nome, cd_id)
SELECT v.nome, 1 FROM (VALUES ('Loja 01'), ('Loja 02'), ('Loja 03')) AS v(nome)
WHERE NOT EXISTS (SELECT 1 FROM loja);
