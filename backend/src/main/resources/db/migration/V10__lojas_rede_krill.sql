-- Lojas da Rede Krill (número e nome, como no Gestão CD) para os links de agendamento por loja.
-- Todas são abastecidas pelos CDs 801 e 806 no calendário de expedição, por isso o CD fica em branco.
-- Idempotente: só insere loja que ainda não existe com o mesmo nome.

INSERT INTO loja (nome)
SELECT v.nome FROM (VALUES
    ('Loja 01 - KRILL P. GRANDE'),
    ('Loja 02 - MERI KRILL'),
    ('Loja 03 - UNIAO SAO VICENTE'),
    ('Loja 04 - KRILL ARESTA'),
    ('Loja 05 - ALMEIDA ROCHA'),
    ('Loja 07 - KRILL DE SANTOS'),
    ('Loja 08 - KRILL DE CUBATAO'),
    ('Loja 10 - KRILL ITANHAEM'),
    ('Loja 12 - ALMEIDA ROCHA 2'),
    ('Loja 13 - VICENTE CARVALHO'),
    ('Loja 14 - KRILL BERTIOGA'),
    ('Loja 15 - KRILL PERUIBE'),
    ('Loja 16 - ROCHA PRAIA GRANDE'),
    ('Loja 17 - KRILL BORACEIA'),
    ('Loja 18 - KRILL CASQUEIRO'),
    ('Loja 19 - KRILL CAICARA'),
    ('Loja 20 - KRILL SAMAMBAIA'),
    ('Loja 21 - NOVA CINTRA'),
    ('Loja 22 - KRILL BOICUCANGA'),
    ('Loja 24 - KRILL ENSEADA'),
    ('Loja 25 - KRILL FREI GASPAR'),
    ('Loja 26 - KRILL PADRE ANCHIETA'),
    ('Loja 27 - KRILL GONZAGA'),
    ('Loja 28 - KRILL MARACANA')
) AS v(nome)
WHERE NOT EXISTS (SELECT 1 FROM loja l WHERE lower(l.nome) = lower(v.nome));

-- Lojas de exemplo da V9: removidas se nunca foram usadas; senão, só inativadas.
DELETE FROM loja l
WHERE l.nome IN ('Loja 01', 'Loja 02', 'Loja 03')
  AND NOT EXISTS (SELECT 1 FROM capacidade_recebimento c WHERE lower(c.loja) = lower(l.nome))
  AND NOT EXISTS (SELECT 1 FROM agendamento_logistica a WHERE lower(a.loja) = lower(l.nome))
  AND NOT EXISTS (SELECT 1 FROM movimento_palete m WHERE lower(m.loja) = lower(l.nome))
  AND NOT EXISTS (SELECT 1 FROM link_agendamento k WHERE lower(k.loja) = lower(l.nome));
UPDATE loja SET ativo = FALSE WHERE nome IN ('Loja 01', 'Loja 02', 'Loja 03');
