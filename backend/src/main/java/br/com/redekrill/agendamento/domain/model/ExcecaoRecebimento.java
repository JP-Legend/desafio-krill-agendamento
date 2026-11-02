package br.com.redekrill.agendamento.domain.model;

import br.com.redekrill.agendamento.domain.exception.RegraNegocioException;

import java.time.LocalDate;

/** Data específica em que o destino fica fechado (ex.: inventário) ou abre excepcionalmente (ex.: num feriado). */
public record ExcecaoRecebimento(Long id, Long cdId, String loja, LocalDate data, boolean aberto, String motivo) {
    public ExcecaoRecebimento {
        loja = Textos.opcional(loja, "Loja", 80);
        motivo = Textos.opcional(motivo, "Motivo", 120);
        if ((cdId == null) == (loja == null)) throw new RegraNegocioException("Informe o CD ou a loja (apenas um dos dois)");
        if (data == null) throw new RegraNegocioException("Data é obrigatória");
    }
}
