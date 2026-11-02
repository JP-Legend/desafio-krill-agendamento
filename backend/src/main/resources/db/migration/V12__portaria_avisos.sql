-- Portaria (porte de /recebimento-operacional/portaria/ do Gestão CD): saída do caminhão registrada na portaria.
ALTER TABLE agendamento_logistica ADD COLUMN saida_em TIMESTAMPTZ;

-- Sino do topo: avisos gerados pelos eventos (novo agendamento, link do fornecedor, chegada, capacidade)
CREATE TABLE aviso (
    id         BIGSERIAL PRIMARY KEY,
    titulo     VARCHAR(80)  NOT NULL,
    mensagem   VARCHAR(300) NOT NULL,
    url        VARCHAR(200),
    criado_em  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Até onde cada usuário já leu (botão "Marcar lidas")
CREATE TABLE aviso_leitura (
    usuario        VARCHAR(60) PRIMARY KEY,
    ultimo_lido_id BIGINT      NOT NULL DEFAULT 0,
    lido_em        TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 1208 Portaria: consultar chegadas e saídas
INSERT INTO perfil_permissao (perfil_id, codigo)
SELECT p.id, 1208 FROM perfil_acesso p WHERE p.nome IN ('Administrador', 'Recebimento', 'Portaria', 'Consulta')
ON CONFLICT DO NOTHING;
