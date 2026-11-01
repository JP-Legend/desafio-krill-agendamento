package br.com.redekrill.agendamento.domain.model;

import java.time.OffsetDateTime;

/**
 * Link público de agendamento (porte do Gestão CD): fixo por CD, por loja ou para todos os CDs
 * (cdId e loja nulos, o fornecedor escolhe o CD). Quem abre só vê o formulário em branco.
 */
public record LinkAgendamento(Long id, String token, Long cdId, String loja, boolean ativo, String criadoPor,
                              OffsetDateTime criadoEm, String revogadoPor, OffsetDateTime revogadoEm) {

    public LinkAgendamento {
        loja = Textos.opcional(loja, "Loja", 80);
        if (cdId != null && loja != null) throw new br.com.redekrill.agendamento.domain.exception.RegraNegocioException(
                "Escolha um CD ou uma loja");
    }

    public static LinkAgendamento novo(String token, Long cdId, String loja, String usuario, OffsetDateTime agora) {
        return new LinkAgendamento(null, token, cdId, loja, true, usuario, agora, null, null);
    }

    public boolean todosOsCds() {
        return cdId == null && loja == null;
    }
}
