-- Destino (CD ou loja) do vale-palete e da cobrança, para o escopo do usuário.
ALTER TABLE vale_palete ADD COLUMN cd_id BIGINT REFERENCES centro_distribuicao (id);
ALTER TABLE vale_palete ADD COLUMN loja VARCHAR(80);
ALTER TABLE cobranca_palete ADD COLUMN cd_id BIGINT REFERENCES centro_distribuicao (id);
ALTER TABLE cobranca_palete ADD COLUMN loja VARCHAR(80);

-- Vales existentes herdam o destino do agendamento (loja da entrega direta ou CD); sem agendamento ficam
-- sem destino (só quem vê todos os destinos enxerga).
UPDATE vale_palete v SET loja = l.loja
FROM agendamento_logistica l WHERE l.agendamento_id = v.agendamento_id AND l.loja IS NOT NULL AND l.loja <> '';
UPDATE vale_palete v SET cd_id = COALESCE(a.cd_id, 1)
FROM agendamento a WHERE a.id = v.agendamento_id AND v.loja IS NULL;
