package br.com.redekrill.agendamento.application.port;

import br.com.redekrill.agendamento.domain.model.LinkAgendamento;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface LinkAgendamentoRepositorio {
    List<LinkAgendamento> listar();

    Optional<LinkAgendamento> buscarPorId(Long id);

    Optional<LinkAgendamento> buscarAtivoPorToken(String token);

    /** Desativa o link ativo do mesmo destino (CD, loja ou todos os CDs). */
    void revogarAtivos(Long cdId, String loja, String usuario, OffsetDateTime quando);

    void revogar(Long id, String usuario, OffsetDateTime quando);

    LinkAgendamento salvar(LinkAgendamento link);
}
