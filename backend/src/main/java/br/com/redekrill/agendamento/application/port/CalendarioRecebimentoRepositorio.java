package br.com.redekrill.agendamento.application.port;

import br.com.redekrill.agendamento.domain.model.CalendarioRecebimento;
import br.com.redekrill.agendamento.domain.model.ExcecaoRecebimento;
import br.com.redekrill.agendamento.domain.model.Feriado;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Calendário de recebimento por destino (CD ou loja), exceções por data e feriados. */
public interface CalendarioRecebimentoRepositorio {
    Optional<CalendarioRecebimento> buscar(Long cdId, String loja);

    List<CalendarioRecebimento> listar();

    CalendarioRecebimento salvar(CalendarioRecebimento c);

    List<ExcecaoRecebimento> excecoes(Long cdId, String loja, LocalDate inicio, LocalDate fim);

    Optional<ExcecaoRecebimento> buscarExcecao(Long id);

    ExcecaoRecebimento salvarExcecao(ExcecaoRecebimento e);

    void excluirExcecao(Long id);

    List<Feriado> feriados(LocalDate inicio, LocalDate fim);
}
