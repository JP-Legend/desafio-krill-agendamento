-- O link "todos os CDs" deixou de existir: cada link é de um CD ou de uma loja.
-- Links antigos sem CD nem loja são revogados (continuam na lista, como revogados).
UPDATE link_agendamento SET ativo = FALSE, revogado_por = 'sistema', revogado_em = now()
WHERE ativo AND cd_id IS NULL AND loja IS NULL;
