-- Perfil Portaria (como no Gestão CD): só a Portaria — ver chegadas e saídas (1208), registrar chegada,
-- não comparecimento e saída (1204) e encaminhar à doca (1206). O escopo por CD/loja vem do usuário.
INSERT INTO perfil_acesso (nome, descricao)
SELECT 'Portaria', 'Portaria: chegada, não comparecimento, doca e saída dos caminhões'
WHERE NOT EXISTS (SELECT 1 FROM perfil_acesso WHERE lower(nome) = 'portaria');
UPDATE perfil_acesso SET descricao = 'Portaria: chegada, não comparecimento, doca e saída dos caminhões' WHERE lower(nome) = 'portaria';
DELETE FROM perfil_permissao WHERE perfil_id = (SELECT id FROM perfil_acesso WHERE lower(nome) = 'portaria');
INSERT INTO perfil_permissao (perfil_id, codigo)
SELECT p.id, c.codigo FROM perfil_acesso p CROSS JOIN (VALUES (1204), (1206), (1208)) AS c(codigo) WHERE lower(p.nome) = 'portaria';
